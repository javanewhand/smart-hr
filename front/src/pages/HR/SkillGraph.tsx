import { useState, useEffect } from 'react'
import {
  Card, Table, Tag, Input, Tabs, Space, Button, Descriptions, message, Empty,
  Row, Col, Statistic, Timeline, List, Select,
} from 'antd'
import {
  SearchOutlined, NodeIndexOutlined, ApartmentOutlined,
  RocketOutlined, BulbOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { skillApi, SkillNode, SkillCategories } from '../../api/skill'
import { positionApi, Position } from '../../api/position'

const { Search } = Input

const CATEGORY_LABELS: Record<string, string> = {
  BACKEND: '后端开发', FRONTEND: '前端开发', DATABASE: '数据库',
  DEVOPS: 'DevOps', AI: '人工智能', TESTING: '测试',
  MANAGEMENT: '项目管理', GENERAL: '通用能力',
}

const SkillGraph = () => {
  const [skills, setSkills] = useState<SkillNode[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [selectedSkill, setSelectedSkill] = useState<SkillNode | null>(null)
  const [categories, setCategories] = useState<SkillCategories>({})
  const [activeCategory, setActiveCategory] = useState<string>('')
  const [learningPath, setLearningPath] = useState<string[]>([])
  const [skillCount, setSkillCount] = useState(0)

  // 推荐相关状态
  const [positions, setPositions] = useState<Position[]>([])
  const [selectedPositionId, setSelectedPositionId] = useState<number | null>(null)
  const [currentSkillsInput, setCurrentSkillsInput] = useState('')
  const [recommendedSkills, setRecommendedSkills] = useState<string[]>([])

  useEffect(() => {
    fetchCategories()
    fetchSkillCount()
    fetchPositions()
  }, [])

  useEffect(() => {
    if (keyword) {
      searchSkills()
    } else if (activeCategory) {
      loadCategorySkills(activeCategory)
    } else {
      fetchAllSkills()
    }
  }, [keyword, activeCategory])

  const fetchCategories = async () => {
    try {
      const data = await skillApi.getCategories()
      setCategories(data)
    } catch {
      // ignore
    }
  }

  const fetchSkillCount = async () => {
    try {
      const count = await skillApi.getCount()
      setSkillCount(count)
    } catch {
      // ignore
    }
  }

  const fetchPositions = async () => {
    try {
      const data = await positionApi.getAll()
      setPositions(data)
    } catch {
      // ignore
    }
  }

  const fetchAllSkills = async () => {
    setLoading(true)
    try {
      const data = await skillApi.search()
      setSkills(data)
    } catch {
      message.error('获取技能列表失败')
    } finally {
      setLoading(false)
    }
  }

  const searchSkills = async () => {
    setLoading(true)
    try {
      const data = await skillApi.search(keyword)
      setSkills(data)
    } catch {
      message.error('搜索失败')
    } finally {
      setLoading(false)
    }
  }

  const loadCategorySkills = async (category: string) => {
    setActiveCategory(category)
    setKeyword('')
    const names = categories[category] || []
    if (names.length === 0) {
      setSkills([])
      return
    }
    setLoading(true)
    try {
      const results: SkillNode[] = []
      for (const name of names) {
        try {
          const skill = await skillApi.getDetail(name)
          results.push(skill)
        } catch {
          results.push({ name, level: 1, description: '', keywords: [], requires: [], relatedTo: [] })
        }
      }
      setSkills(results)
    } catch {
      message.error('获取分类技能失败')
    } finally {
      setLoading(false)
    }
  }

  const handleViewDetail = async (skill: SkillNode) => {
    try {
      const detail = await skillApi.getDetail(skill.name)
      setSelectedSkill(detail)
      const path = await skillApi.getLearningPath(skill.name)
      setLearningPath(path)
    } catch {
      message.error('获取技能详情失败')
    }
  }

  const handleRecommend = async () => {
    if (!currentSkillsInput.trim()) {
      message.warning('请输入当前技能')
      return
    }

    let targetSkills: string[] = []
    if (selectedPositionId) {
      const position = positions.find((p) => p.id === selectedPositionId)
      targetSkills = position?.skills || []
    }

    const currentSkills = currentSkillsInput.split(',').map((s) => s.trim()).filter(Boolean)
    if (targetSkills.length === 0) {
      message.warning('请选择目标岗位')
      return
    }

    try {
      const data = await skillApi.recommendSkills({ currentSkills, targetSkills })
      setRecommendedSkills(data)
    } catch {
      message.error('推荐失败')
    }
  }

  const categoryTabs = Object.entries(categories).map(([code, names]) => ({
    key: code,
    label: `${CATEGORY_LABELS[code] || code} (${names.length})`,
  }))

  const columns: ColumnsType<SkillNode> = [
    {
      title: '技能名称', dataIndex: 'name', key: 'name', width: 180,
      render: (name: string, record) => (
        <Button type="link" onClick={() => handleViewDetail(record)}>{name}</Button>
      ),
    },
    {
      title: '等级', dataIndex: 'level', key: 'level', width: 100,
      render: (level: number) => {
        const colors = ['', '#52c41a', '#1890ff', '#722ed1', '#faad14', '#f5222d']
        return <Tag color={colors[level] || '#999'}>Lv.{level}</Tag>
      },
    },
    {
      title: '描述', dataIndex: 'description', key: 'description',
      render: (desc: string) => desc || '-',
    },
    {
      title: '关键词', dataIndex: 'keywords', key: 'keywords',
      render: (keywords: string[]) => (
        <Space wrap>
          {keywords?.slice(0, 5).map((kw) => <Tag key={kw}>{kw}</Tag>)}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card>
            <Row gutter={16} align="middle">
              <Col flex="auto">
                <Search
                  placeholder="搜索技能名称或关键词"
                  allowClear
                  value={keyword}
                  onChange={(e) => {
                    setKeyword(e.target.value)
                    setActiveCategory('')
                  }}
                  onSearch={searchSkills}
                  style={{ maxWidth: 400 }}
                  prefix={<SearchOutlined />}
                />
              </Col>
              <Col>
                <Statistic title="技能总数" value={skillCount} prefix={<NodeIndexOutlined />} />
              </Col>
            </Row>
          </Card>
        </Col>

        {/* 左侧：分类 + 列表 */}
        <Col xs={24} lg={selectedSkill ? 12 : 24}>
          <Card>
            <Tabs
              activeKey={activeCategory}
              onChange={(key) => { loadCategorySkills(key) }}
              tabBarExtraContent={
                activeCategory ? (
                  <Button size="small" onClick={() => { setActiveCategory(''); setKeyword(''); fetchAllSkills() }}>
                    显示全部
                  </Button>
                ) : null
              }
              items={[
                { key: '', label: '全部' },
                ...categoryTabs,
              ]}
            />
            <Table
              columns={columns}
              dataSource={skills}
              rowKey="name"
              loading={loading}
              size="small"
              pagination={{ pageSize: 15, showTotal: (total) => `共 ${total} 项` }}
              locale={{ emptyText: <Empty description="选择分类或搜索技能" /> }}
            />
          </Card>
        </Col>

        {/* 右侧：技能详情 */}
        {selectedSkill && (
          <Col xs={24} lg={12}>
            <Card title={`${selectedSkill.name} 详情`} extra={<Button size="small" onClick={() => setSelectedSkill(null)}>关闭</Button>}>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="等级">
                  <Tag color="#1890ff">Lv.{selectedSkill.level}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="描述">{selectedSkill.description || '-'}</Descriptions.Item>
                <Descriptions.Item label="关键词">
                  <Space wrap>
                    {selectedSkill.keywords?.map((kw) => <Tag key={kw}>{kw}</Tag>)}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="前置依赖">
                  {selectedSkill.requires?.length ? (
                    <Space wrap>
                      {selectedSkill.requires.map((s) => (
                        <Button key={s.name} type="link" size="small" onClick={() => handleViewDetail(s)}>
                          {s.name}
                        </Button>
                      ))}
                    </Space>
                  ) : '无（基础技能）'}
                </Descriptions.Item>
                <Descriptions.Item label="相关技能">
                  {selectedSkill.relatedTo?.length ? (
                    <Space wrap>
                      {selectedSkill.relatedTo.map((s) => (
                        <Button key={s.name} type="link" size="small" onClick={() => handleViewDetail(s)}>
                          {s.name}
                        </Button>
                      ))}
                    </Space>
                  ) : '无'}
                </Descriptions.Item>
              </Descriptions>

              {learningPath.length > 0 && (
                <Card title={<><ApartmentOutlined /> 学习路径</>} size="small" style={{ marginTop: 16 }}>
                  <Timeline
                    items={learningPath.map((name, idx) => ({
                      color: idx === learningPath.length - 1 ? 'green' : 'blue',
                      children: <Tag color={idx === learningPath.length - 1 ? 'green' : 'blue'}>{name}</Tag>,
                    }))}
                  />
                </Card>
              )}
            </Card>
          </Col>
        )}
      </Row>

      {/* 技能推荐 */}
      <Card title={<><BulbOutlined /> 技能学习推荐</>} style={{ marginTop: 24 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <label style={{ display: 'block', marginBottom: 8 }}>我目前掌握的技能</label>
            <Input.TextArea
              rows={3}
              placeholder="多个技能用逗号分隔，如：Java, MySQL, Git"
              value={currentSkillsInput}
              onChange={(e) => setCurrentSkillsInput(e.target.value)}
            />
          </Col>
          <Col xs={24} md={8}>
            <label style={{ display: 'block', marginBottom: 8 }}>目标岗位</label>
            <Select
              showSearch
              placeholder="选择岗位以获取技能要求"
              style={{ width: '100%' }}
              value={selectedPositionId}
              onChange={(id) => setSelectedPositionId(id)}
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
              options={positions.map((p) => ({ label: p.title, value: p.id }))}
            />
            {selectedPositionId && (
              <div style={{ marginTop: 8 }}>
                {positions.find((p) => p.id === selectedPositionId)?.skills?.map((s) => (
                  <Tag key={s} color="blue">{s}</Tag>
                ))}
              </div>
            )}
          </Col>
          <Col xs={24} md={8} style={{ display: 'flex', alignItems: 'flex-end' }}>
            <Button type="primary" icon={<RocketOutlined />} onClick={handleRecommend} block>
              推荐学习技能
            </Button>
          </Col>
        </Row>
        {recommendedSkills.length > 0 && (
          <Card size="small" style={{ marginTop: 16, background: '#f6ffed' }}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>建议按顺序学习：</div>
            <Timeline
              items={recommendedSkills.map((skill, idx) => ({
                color: 'green',
                children: (
                  <Button type="link" size="small" onClick={() => handleViewDetail({ name: skill, level: 0, description: '', keywords: [], requires: [], relatedTo: [] })}>
                    {idx + 1}. {skill}
                  </Button>
                ),
              }))}
            />
          </Card>
        )}
      </Card>
    </div>
  )
}

export default SkillGraph
