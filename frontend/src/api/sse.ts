export interface SseMessage {
  id?: string
  event: string
  data: string
  retry?: number
}

interface PendingMessage {
  id?: string
  event?: string
  data: string[]
  retry?: number
}

function emptyMessage(): PendingMessage {
  return { data: [] }
}

export class SseParser {
  private buffer = ''
  private pending = emptyMessage()

  feed(chunk: string): SseMessage[] {
    this.buffer += chunk
    const messages: SseMessage[] = []
    let lineStart = 0
    for (let index = 0; index < this.buffer.length; index += 1) {
      const character = this.buffer[index]
      if (character !== '\n' && character !== '\r') continue
      if (character === '\r' && index === this.buffer.length - 1) break

      const message = this.consumeLine(this.buffer.slice(lineStart, index))
      if (message) messages.push(message)
      if (character === '\r' && this.buffer[index + 1] === '\n') index += 1
      lineStart = index + 1
    }
    this.buffer = this.buffer.slice(lineStart)
    return messages
  }

  finish(): SseMessage[] {
    const messages: SseMessage[] = []
    if (this.buffer.length > 0) {
      const finalLine = this.buffer.endsWith('\r') ? this.buffer.slice(0, -1) : this.buffer
      const message = this.consumeLine(finalLine)
      if (message) messages.push(message)
      this.buffer = ''
    }
    const finalMessage = this.dispatch()
    if (finalMessage) messages.push(finalMessage)
    return messages
  }

  private consumeLine(line: string): SseMessage | undefined {
    if (line === '') return this.dispatch()
    if (line.startsWith(':')) return undefined

    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'data') this.pending.data.push(value)
    else if (field === 'event') this.pending.event = value
    else if (field === 'id' && !value.includes('\0')) this.pending.id = value
    else if (field === 'retry' && /^\d+$/.test(value)) this.pending.retry = Number(value)
    return undefined
  }

  private dispatch(): SseMessage | undefined {
    if (this.pending.data.length === 0) {
      this.pending = emptyMessage()
      return undefined
    }
    const message: SseMessage = {
      event: this.pending.event || 'message',
      data: this.pending.data.join('\n'),
    }
    if (this.pending.id !== undefined) message.id = this.pending.id
    if (this.pending.retry !== undefined) message.retry = this.pending.retry
    this.pending = emptyMessage()
    return message
  }
}
