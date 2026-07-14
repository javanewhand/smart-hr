import request from './request'

export interface SkillNode {
  name: string
  level: number
  description: string
  keywords: string[]
  requires: SkillNode[]
  relatedTo: SkillNode[]
}

export interface SkillCategories {
  [category: string]: string[]
}

export interface RecommendRequest {
  currentSkills: string[]
  targetSkills: string[]
}

export const skillApi = {
  search: async (keyword?: string): Promise<SkillNode[]> => {
    const params = keyword ? { keyword } : {}
    return await request.get<SkillNode[]>('/hr/skills', { params })
  },

  getCount: async (): Promise<number> => {
    return await request.get<number>('/hr/skills/count')
  },

  getDetail: async (skillName: string): Promise<SkillNode> => {
    return await request.get<SkillNode>(`/hr/skills/${encodeURIComponent(skillName)}`)
  },

  getCategories: async (): Promise<SkillCategories> => {
    return await request.get<SkillCategories>('/hr/skills/categories')
  },

  getLearningPath: async (skillName: string): Promise<string[]> => {
    return await request.get<string[]>(`/hr/skills/${encodeURIComponent(skillName)}/learning-path`)
  },

  recommendSkills: async (data: RecommendRequest): Promise<string[]> => {
    return await request.post<string[]>('/hr/skills/recommend', data)
  },
}
