import { onMounted } from 'vue'

export function useTheme() {
  const applyTheme = () => {
    document.documentElement.classList.add('dark')
  }

  onMounted(() => {
    applyTheme()
  })

  return {
    theme: { value: 'dark' },
    toggleTheme: () => {},
    setTheme: () => {}
  }
}
