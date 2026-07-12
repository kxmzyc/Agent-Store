function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function highlightKeyword(text, keyword) {
  if (!text) return ''
  const escapedText = escapeHtml(text)
  const normalizedKeyword = String(keyword || '').trim()
  if (!normalizedKeyword) return escapedText
  const re = new RegExp(`(${escapeRegExp(escapeHtml(normalizedKeyword))})`, 'gi')
  return escapedText.replace(re, '<mark>$1</mark>')
}
