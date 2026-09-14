export async function streamSse(
  url: string,
  token: string,
  body: unknown,
  onDelta: (value: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  return streamSseEvents(url, token, body, event => {
    if (event.data !== '[DONE]') onDelta(event.data)
  }, signal)
}

export interface SseEvent { event: string; data: string; id?: string }

export async function streamSseEvents(
  url: string,
  token: string,
  body: unknown,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-Request-Id': crypto.randomUUID().replaceAll('-', ''),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) throw new Error(`SSE request failed: ${response.status}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replaceAll('\r\n', '\n')
      const events = buffer.split('\n\n')
      buffer = events.pop() ?? ''
      for (const event of events) {
        const lines = event.split('\n')
        const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
        if (data) onEvent({
          event: lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message',
          data,
          id: lines.find(line => line.startsWith('id:'))?.slice(3).trim(),
        })
      }
    }
  } catch (error) {
    await reader.cancel().catch(() => undefined)
    throw error
  } finally {
    reader.releaseLock()
  }
}
