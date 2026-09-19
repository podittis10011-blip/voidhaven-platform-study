export const FIELD_OPTION_LABELS: Record<string, string> = {
  GUIDE: '指南',
  COURSE: '课程',
  TOOL: '工具',
  DOCUMENT: '文档',
  ARTICLE: '文章 / 经验',
  ORIGINAL: '原创',
  EXTERNAL: '外部来源',
}

export function fieldOptionLabel(option: string) {
  return FIELD_OPTION_LABELS[option] ?? option
}
