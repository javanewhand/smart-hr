/**
 * 个人设置页面
 *
 * @author QinFeng Luo
 * @date 2026/07/13
 */
import { useState } from 'react'
import { Card, Form, Input, Button, Select, Divider, message, Modal } from 'antd'
import { UserOutlined, MailOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/auth'

const Settings = () => {
  const { user, updateUser } = useAuthStore()
  const navigate = useNavigate()
  const [profileLoading, setProfileLoading] = useState(false)
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [profileForm] = Form.useForm()
  const [passwordForm] = Form.useForm()

  const handleUpdateProfile = async (values: { username: string; email: string; role: string }) => {
    setProfileLoading(true)
    try {
      const updated = await authApi.updateProfile(values)
      updateUser(updated)
      message.success('个人资料更新成功')

      if (values.role !== user?.role) {
        Modal.info({
          title: '角色已变更',
          content: `您的角色已从 ${user?.role === 'HR' ? 'HR' : '面试官'} 切换为 ${values.role === 'HR' ? 'HR' : '面试官'}，点击确定跳转到对应主页。`,
          onOk: () => {
            navigate(values.role === 'HR' ? '/hr/dashboard' : '/interviewer/dashboard')
          },
        })
      }
    } catch {
      // 错误已在拦截器中处理
    } finally {
      setProfileLoading(false)
    }
  }

  const handleChangePassword = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    setPasswordLoading(true)
    try {
      await authApi.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      message.success('密码修改成功，请妥善保管新密码')
      passwordForm.resetFields()
    } catch {
      // 错误已在拦截器中处理
    } finally {
      setPasswordLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '24px 0' }}>
      <Card title="个人设置" bordered={false}>
        {/* 基本资料 */}
        <Divider orientation="left" plain>
          <UserOutlined style={{ marginRight: 8 }} />
          基本资料
        </Divider>

        <Form
          form={profileForm}
          layout="vertical"
          initialValues={{
            username: user?.username || '',
            email: user?.email || '',
            role: user?.role || 'INTERVIEWER',
          }}
          onFinish={handleUpdateProfile}
          style={{ maxWidth: 480 }}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 2, max: 50, message: '用户名长度需在2-50个字符之间' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
          </Form.Item>

          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
          >
            <Input prefix={<MailOutlined />} placeholder="请输入邮箱" />
          </Form.Item>

          <Form.Item
            name="role"
            label="角色"
            rules={[{ required: true, message: '请选择角色' }]}
            extra="切换角色后将自动跳转到对应主页"
          >
            <Select
              options={[
                { label: 'HR', value: 'HR' },
                { label: '面试官', value: 'INTERVIEWER' },
              ]}
            />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={profileLoading}>
              保存修改
            </Button>
          </Form.Item>
        </Form>

        {/* 修改密码 */}
        <Divider orientation="left" plain style={{ marginTop: 32 }}>
          <LockOutlined style={{ marginRight: 8 }} />
          修改密码
        </Divider>

        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={handleChangePassword}
          style={{ maxWidth: 480 }}
        >
          <Form.Item
            name="oldPassword"
            label="原密码"
            rules={[{ required: true, message: '请输入原密码' }]}
          >
            <Input.Password prefix={<SafetyOutlined />} placeholder="请输入原密码" />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码长度不能少于6个字符' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码（至少6位）" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请再次输入新密码" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={passwordLoading} danger>
              修改密码
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

export default Settings
