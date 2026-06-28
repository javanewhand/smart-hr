/**
 * 简历管理服务
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.hr;

import com.smarthr.config.MinioProperties;
import com.smarthr.dto.resume.ResumeDTO;
import com.smarthr.dto.resume.ResumeUploadResponse;
import com.smarthr.entity.Resume;
import com.smarthr.exception.BusinessException;
import com.smarthr.repository.ResumeRepository;
import com.smarthr.service.document.DocumentParser;
import com.smarthr.service.document.SkillExtractor;
import com.smarthr.service.rag.EmbeddingService;
import com.smarthr.service.vector.MilvusVectorStore;
import com.smarthr.service.vector.VectorDocument;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final DocumentParser documentParser;
    private final SkillExtractor skillExtractor;
    private final MilvusVectorStore milvusVectorStore;
    private final EmbeddingService embeddingService;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public ResumeService(ResumeRepository resumeRepository,
                         DocumentParser documentParser,
                         SkillExtractor skillExtractor,
                         MilvusVectorStore milvusVectorStore,
                         EmbeddingService embeddingService,
                         MinioClient minioClient,
                         MinioProperties minioProperties) {
        this.resumeRepository = resumeRepository;
        this.documentParser = documentParser;
        this.skillExtractor = skillExtractor;
        this.milvusVectorStore = milvusVectorStore;
        this.embeddingService = embeddingService;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    /**
     * 上传并解析简历
     */
    @Transactional
    public ResumeUploadResponse uploadResume(MultipartFile file, Long userId) {
        // 验证文件
        validateFile(file);

        try {
            // 解析文档内容
            String content = documentParser.parse(file);

            // 提取技能
            List<String> extractedSkills = skillExtractor.extractSkills(content, userId);
            List<String> normalizedSkills = skillExtractor.normalizeSkills(extractedSkills);

            // 上传文件到 MinIO
            String objectName = uploadToMinio(file, userId);

            // 保存简历记录
            Resume resume = Resume.builder()
                    .userId(userId)
                    .fileName(file.getOriginalFilename())
                    .filePath(objectName)
                    .content(content)
                    .extractedSkills(normalizedSkills)
                    .createdAt(LocalDateTime.now())
                    .build();

            Resume saved = resumeRepository.save(resume);
            log.info("Uploaded resume: {} for user {}", saved.getId(), userId);

            // 将简历添加到向量存储
            indexResume(saved);

            return ResumeUploadResponse.builder()
                    .resumeId(saved.getId())
                    .fileName(file.getOriginalFilename())
                    .parsedContent(truncateContent(content, 500))
                    .extractedSkills(normalizedSkills)
                    .message("简历上传并解析成功")
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload resume for user {}: {}", userId, e.getMessage());
            throw new BusinessException("简历上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取简历详情
     */
    public ResumeDTO getResume(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("简历不存在"));
        return toDTO(resume);
    }

    /**
     * 分页查询简历列表
     */
    public Page<ResumeDTO> listResumes(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Resume> resumes;
        if (keyword != null && !keyword.isEmpty()) {
            resumes = resumeRepository.findByFileNameContainingIgnoreCase(keyword, pageable);
        } else {
            resumes = resumeRepository.findAll(pageable);
        }

        return resumes.map(this::toDTO);
    }

    /**
     * 获取用户的所有简历
     */
    public List<ResumeDTO> getResumesByUserId(Long userId) {
        return resumeRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 删除简历
     */
    @Transactional
    public void deleteResume(Long id, Long userId) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("简历不存在"));

        // 从 MinIO 删除文件
        deleteFromMinio(resume.getFilePath());

        // 删除向量
        milvusVectorStore.deleteDocument("resume_" + id);

        // 删除记录
        resumeRepository.deleteById(id);
        log.info("Deleted resume: {} by user {}", id, userId);
    }

    /**
     * 获取所有简历（用于匹配）
     */
    public List<ResumeDTO> getAllResumes() {
        return resumeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }

        String filename = file.getOriginalFilename();
        if (!documentParser.isSupported(filename)) {
            throw new BusinessException("不支持的文件格式，请上传 PDF、Word 或 TXT 文件");
        }

        // 文件大小限制 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 10MB");
        }
    }

    /**
     * 上传文件到 MinIO
     */
    private String uploadToMinio(MultipartFile file, Long userId) throws Exception {
        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = userId + "/" + UUID.randomUUID() + extension;

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        log.info("Uploaded resume to MinIO: {}/{}", minioProperties.getBucket(), objectName);
        return objectName;
    }

    /**
     * 从 MinIO 删除文件
     */
    private void deleteFromMinio(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build());
            log.info("Deleted resume from MinIO: {}", objectName);
        } catch (Exception e) {
            log.warn("Failed to delete resume from MinIO: {}", objectName, e);
        }
    }

    /**
     * 从 MinIO 获取文件流（供下载/预览使用）
     */
    public InputStream getFileStream(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get resume from MinIO: {}", objectName, e);
            throw new BusinessException("获取简历文件失败");
        }
    }

    /**
     * 获取简历文件名
     */
    public String getFileName(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("简历不存在"));
        return resume.getFileName();
    }

    /**
     * 将简历索引到向量存储
     */
    private void indexResume(Resume resume) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("resumeId", resume.getId());
            metadata.put("userId", resume.getUserId());
            metadata.put("fileName", resume.getFileName());
            if (resume.getExtractedSkills() != null) {
                metadata.put("skills", String.join(",", resume.getExtractedSkills()));
            }

            float[] embedding = embeddingService.embed(resume.getContent());

            VectorDocument document = VectorDocument.builder()
                    .id("resume_" + resume.getId())
                    .content(resume.getContent())
                    .embedding(embedding)
                    .metadata(metadata)
                    .build();

            milvusVectorStore.addDocuments(List.of(document));
            log.debug("Indexed resume {} to vector store", resume.getId());
        } catch (Exception e) {
            log.error("Failed to index resume {}: {}", resume.getId(), e.getMessage());
        }
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 转换为 DTO
     */
    private ResumeDTO toDTO(Resume resume) {
        return ResumeDTO.builder()
                .id(resume.getId())
                .userId(resume.getUserId())
                .fileName(resume.getFileName())
                .filePath(resume.getFilePath())
                .content(resume.getContent())
                .extractedSkills(resume.getExtractedSkills())
                .createdAt(resume.getCreatedAt())
                .build();
    }
}
