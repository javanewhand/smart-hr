/**
 * 面试题生成历史页面
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
import { useEffect, useState } from 'react'
import { Card, Table, Tag, Button, Modal, List, Typography, message, Checkbox, Space } from 'antd'
import { EyeOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { interviewApi, InterviewRecord } from '../../api/interview'

const { Text, Paragraph } = Typography

const QuestionHistory = () => {
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [selectedRecord, setSelectedRecord] = useState<InterviewRecord | null>(null)
  const [records, setRecords] = useState<InterviewRecord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [selectedIndices, setSelectedIndices] = useState<Set<number>>(new Set())

  useEffect(() => {
    fetchRecords(page, pageSize)
  }, [page, pageSize])

  const fetchRecords = async (pageNum: number, size: number) => {
    try {
      setLoading(true)
      const res = await interviewApi.getRecords({ page: pageNum - 1, size })
      setRecords(res.content || [])
      setTotal(res.totalElements || 0)
    } catch (e) {
      message.error('获取生成历史失败')
    } finally {
      setLoading(false)
    }
  }

  const getDifficultyTag = (difficulty: string) => {
    switch (difficulty?.toUpperCase()) {
      case 'JUNIOR':
        return <Tag color="green">简单</Tag>
      case 'MIDDLE':
        return <Tag color="orange">中等</Tag>
      case 'SENIOR':
        return <Tag color="red">困难</Tag>
      default:
        return <Tag>{difficulty}</Tag>
    }
  }

  const handleView = (record: InterviewRecord) => {
    setSelectedRecord(record)
    setSelectedIndices(new Set())
    setModalVisible(true)
  }

  const handleApprove = async (index: number) => {
    if (!selectedRecord) return
    try {
      await interviewApi.approveQuestion(selectedRecord.id, index)
      const updated = { ...selectedRecord }
      updated.questions = [...updated.questions]
      updated.questions[index] = { ...updated.questions[index], status: 'APPROVED' }
      setSelectedRecord(updated)
      setRecords((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      message.success('题目已入库')
    } catch {
      message.error('入库失败')
    }
  }

  const handleReject = async (index: number) => {
    if (!selectedRecord) return
    try {
      await interviewApi.rejectQuestion(selectedRecord.id, index)
      const updated = { ...selectedRecord }
      updated.questions = [...updated.questions]
      updated.questions[index] = { ...updated.questions[index], status: 'REJECTED' }
      setSelectedRecord(updated)
      setRecords((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      message.success('题目已弃用')
    } catch {
      message.error('操作失败')
    }
  }

  const handleUnapprove = async (index: number) => {
    if (!selectedRecord) return
    try {
      await interviewApi.unapproveQuestion(selectedRecord.id, index)
      const updated = { ...selectedRecord }
      updated.questions = [...updated.questions]
      updated.questions[index] = { ...updated.questions[index], status: undefined }
      setSelectedRecord(updated)
      setRecords((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      message.success('已取消入库')
    } catch {
      message.error('取消入库失败')
    }
  }

  const toggleSelect = (index: number) => {
    setSelectedIndices((prev) => {
      const next = new Set(prev)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  const toggleSelectAll = () => {
    if (!selectedRecord) return
    const count = selectedRecord.questions.length
    if (selectedIndices.size === count) {
      setSelectedIndices(new Set())
    } else {
      setSelectedIndices(new Set(selectedRecord.questions.map((_, i) => i)))
    }
  }

  const handleBatchApprove = async () => {
    if (!selectedRecord || selectedIndices.size === 0) return
    const indices = Array.from(selectedIndices).filter(
      (i) => (selectedRecord.questions[i] as any).status !== 'APPROVED'
    )
    if (indices.length === 0) {
      message.warning('所选题目均已入库')
      return
    }
    try {
      await interviewApi.batchApproveQuestions(selectedRecord.id, indices)
      const updated = { ...selectedRecord }
      updated.questions = [...updated.questions]
      indices.forEach((i) => {
        updated.questions[i] = { ...updated.questions[i], status: 'APPROVED' }
      })
      setSelectedRecord(updated)
      setRecords((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      setSelectedIndices(new Set())
      message.success(`已批量入库 ${indices.length} 道题目`)
    } catch {
      message.error('批量入库失败')
    }
  }

  const handleBatchUnapprove = async () => {
    if (!selectedRecord || selectedIndices.size === 0) return
    const indices = Array.from(selectedIndices).filter(
      (i) => (selectedRecord.questions[i] as any).status === 'APPROVED'
    )
    if (indices.length === 0) {
      message.warning('所选题目均未入库')
      return
    }
    try {
      await interviewApi.batchUnapproveQuestions(selectedRecord.id, indices)
      const updated = { ...selectedRecord }
      updated.questions = [...updated.questions]
      indices.forEach((i) => {
        updated.questions[i] = { ...updated.questions[i], status: undefined }
      })
      setSelectedRecord(updated)
      setRecords((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      setSelectedIndices(new Set())
      message.success(`已批量取消入库 ${indices.length} 道题目`)
    } catch {
      message.error('批量取消入库失败')
    }
  }

  const getDisplayTitle = (record: InterviewRecord) => {
    if (record.positionTitle) return record.positionTitle

    const skills =
      record.questions
        ?.map((q) => (q as any).skill as string | undefined)
        ?.filter((s) => !!s) || []

    if (skills.length) {
      const unique = Array.from(new Set(skills))
      const shown = unique.slice(0, 3).join(', ')
      return unique.length > 3 ? `按技能生成：${shown} 等` : `按技能生成：${shown}`
    }

    return '按技能生成'
  }

  const columns: ColumnsType<InterviewRecord> = [
    {
      title: '岗位',
      dataIndex: 'positionTitle',
      key: 'positionTitle',
      render: (_value, record) => getDisplayTitle(record),
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      key: 'difficulty',
      render: (difficulty) => getDifficultyTag(difficulty),
    },
    {
      title: '题目数量',
      dataIndex: 'questions',
      key: 'questionCount',
      render: (qs) => `${qs?.length || 0} 道`,
    },
    {
      title: '生成时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>
          查看详情
        </Button>
      ),
    },
  ]

  return (
    <div>
      <Card title="生成历史">
        <Table
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, size) => {
              setPage(p)
              setPageSize(size)
            },
          }}
        />
      </Card>

      <Modal
        title={`面试题详情 - ${selectedRecord ? getDisplayTitle(selectedRecord) : ''}`}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={700}
      >
        {selectedRecord && (
          <>
            <div style={{ marginBottom: 12 }}>
              <Space>
                <Checkbox
                  checked={selectedIndices.size === selectedRecord.questions.length}
                  indeterminate={
                    selectedIndices.size > 0 &&
                    selectedIndices.size < selectedRecord.questions.length
                  }
                  onChange={toggleSelectAll}
                >
                  全选
                </Checkbox>
                <Button
                  size="small"
                  type="primary"
                  onClick={handleBatchApprove}
                  disabled={selectedIndices.size === 0}
                >
                  批量入库
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={handleBatchUnapprove}
                  disabled={selectedIndices.size === 0}
                >
                  批量取消入库
                </Button>
              </Space>
            </div>
            <List
              dataSource={selectedRecord.questions}
              renderItem={(item, index) => (
                <List.Item
                  actions={[
                    <Checkbox
                      key="select"
                      checked={selectedIndices.has(index)}
                      onChange={() => toggleSelect(index)}
                    />,
                    ...((item as any).status === 'APPROVED'
                      ? [
                          <Button
                            key="unapprove"
                            type="link"
                            danger
                            icon={<CloseOutlined />}
                            onClick={() => handleUnapprove(index)}
                          >
                            取消入库
                          </Button>,
                          <Tag color="green" key="tag">已入库</Tag>,
                        ]
                      : (item as any).status === 'REJECTED'
                        ? [<Tag color="default" key="tag">已弃用</Tag>]
                        : [
                            <Button
                              key="approve"
                              type="link"
                              icon={<CheckOutlined />}
                              onClick={() => handleApprove(index)}
                            >
                              入库
                            </Button>,
                            <Button
                              key="reject"
                              type="link"
                              danger
                              icon={<CloseOutlined />}
                              onClick={() => handleReject(index)}
                            >
                              弃用
                            </Button>,
                          ]),
                  ]}
                >
                <List.Item.Meta
                  title={
                    <div>
                      <Text strong>第 {index + 1} 题</Text>
                      {item.type && (
                        <Tag color="blue" style={{ marginLeft: 8 }}>
                          {item.type}
                        </Tag>
                      )}
                      {item.difficulty && (
                        <Tag style={{ marginLeft: 8 }}>{item.difficulty}</Tag>
                      )}
                      {(item as any).status === 'APPROVED' && <Tag color="green" style={{ marginLeft: 8 }}>已入库</Tag>}
                      {(item as any).status === 'REJECTED' && <Tag color="default" style={{ marginLeft: 8 }}>已弃用</Tag>}
                    </div>
                  }
                  description={
                    <div style={{ marginTop: 8 }}>
                      <Paragraph>{(item as any).question || (item as any).content}</Paragraph>
                      {('answerPoints' in item || 'answer' in item) && (
                        <Paragraph type="secondary">
                          <Text strong>参考答案: </Text>
                          {(item as any).answerPoints || (item as any).answer}
                        </Paragraph>
                      )}
                    </div>
                  }
                />
              </List.Item>
            )}
          />
          </>
        )}
      </Modal>
    </div>
  )
}

export default QuestionHistory
