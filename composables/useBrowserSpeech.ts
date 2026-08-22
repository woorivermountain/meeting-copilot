interface SpeechRecognitionEventLike extends Event {
  resultIndex: number
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>
}

export function useBrowserSpeech(onFinal: (text: string) => void) {
  const supported = ref(false)
  const active = ref(false)
  const interim = ref('')
  const error = ref('')
  let recognition: any
  let shouldRestart = false

  onMounted(() => {
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    supported.value = Boolean(Ctor)
    if (!Ctor) return
    recognition = new Ctor()
    recognition.lang = 'ko-KR'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.onresult = (event: SpeechRecognitionEventLike) => {
      let draft = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (!result) continue
        const alternative = result[0]
        if (!alternative) continue
        const text = alternative.transcript.trim()
        if (result.isFinal) onFinal(text)
        else draft += text
      }
      interim.value = draft
    }
    recognition.onerror = (event: { error: string }) => {
      error.value = event.error
      if (event.error === 'not-allowed') shouldRestart = false
    }
    recognition.onend = () => {
      active.value = false
      if (shouldRestart) {
        try { recognition.start(); active.value = true } catch { /* already starting */ }
      }
    }
  })

  function start() {
    if (!recognition) return
    error.value = ''
    shouldRestart = true
    try { recognition.start(); active.value = true } catch { /* already active */ }
  }
  function stop() {
    shouldRestart = false
    recognition?.stop()
    active.value = false
  }
  onBeforeUnmount(stop)
  return { supported, active, interim, error, start, stop }
}
