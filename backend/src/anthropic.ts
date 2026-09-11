/**
 * Hívás az Anthropic Messages API-ra, streamelve.
 *
 * A streamelés nem kényelmi kérdés: nagy `max_tokens` mellett a nem streamelt kérés a
 * HTTP időkorlátba futna, és a felhasználó percekig nem látna semmit.
 */

export interface Usage {
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
}

/** USD milliomod rész / token. A könyvelés ebből számol havi költséget felhasználónként. */
interface Price {
  input: number
  output: number
}

const PRICES: Record<string, Price> = {
  'claude-opus-5': { input: 15, output: 75 },
  'claude-sonnet-5': { input: 3, output: 15 },
  'claude-haiku-4-5': { input: 1, output: 5 },
}

const DEFAULT_PRICE: Price = { input: 3, output: 15 }

/** A cache-ből olvasott bemenet a listaár tizede, a cache írása másfélszerese. */
export function costMicros(model: string, usage: Usage): number {
  const price = PRICES[model] ?? DEFAULT_PRICE
  const input = usage.inputTokens * price.input
  const cacheRead = usage.cacheReadTokens * price.input * 0.1
  const cacheWrite = usage.cacheWriteTokens * price.input * 1.25
  const output = usage.outputTokens * price.output
  return Math.round(input + cacheRead + cacheWrite + output)
}

export interface CallParams {
  apiKey: string
  model: string
  system: string
  user: string
  maxTokens: number
  /** A Haiku nem fogadja el, ezért lehet null. */
  effort: string | null
}

export class AnthropicError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

/**
 * Lefuttat egy hívást, és minden szövegdarabot átad `onDelta`-nak.
 * A visszatérő érték a tényleges tokenfogyasztás — ebből megy a kvóta és a könyvelés.
 */
export async function streamMessage(
  params: CallParams,
  onDelta: (text: string) => void | Promise<void>,
  signal?: AbortSignal,
): Promise<Usage> {
  const body: Record<string, unknown> = {
    model: params.model,
    max_tokens: params.maxTokens,
    stream: true,
    system: [
      {
        type: 'text',
        text: params.system,
        // A rendszerprompt minden hívásnál azonos, ezért cache-elhető: a heti darabok és
        // a javító körök után ez a legnagyobb egyszeri megtakarítás.
        cache_control: { type: 'ephemeral' },
      },
    ],
    messages: [{ role: 'user', content: params.user }],
  }
  if (params.effort) body.output_config = { effort: params.effort }

  const response = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-api-key': params.apiKey,
      'anthropic-version': '2023-06-01',
    },
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok || !response.body) {
    const detail = await response.text().catch(() => '')
    throw new AnthropicError(detail.slice(0, 500) || `HTTP ${response.status}`, response.status)
  }

  const usage: Usage = { inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0 }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let newline = buffer.indexOf('\n')
    while (newline >= 0) {
      const line = buffer.slice(0, newline).trim()
      buffer = buffer.slice(newline + 1)
      newline = buffer.indexOf('\n')
      if (!line.startsWith('data:')) continue

      const payload = line.slice(5).trim()
      if (!payload || payload === '[DONE]') continue

      let event: any
      try {
        event = JSON.parse(payload)
      } catch {
        continue
      }

      switch (event.type) {
        case 'message_start': {
          const u = event.message?.usage ?? {}
          usage.inputTokens += u.input_tokens ?? 0
          usage.cacheReadTokens += u.cache_read_input_tokens ?? 0
          usage.cacheWriteTokens += u.cache_creation_input_tokens ?? 0
          break
        }
        case 'content_block_delta': {
          const text = event.delta?.text
          if (typeof text === 'string' && text.length > 0) await onDelta(text)
          break
        }
        case 'message_delta': {
          usage.outputTokens = event.usage?.output_tokens ?? usage.outputTokens
          break
        }
        case 'error': {
          throw new AnthropicError(event.error?.message ?? 'Ismeretlen hiba az AI szolgáltatásnál.', 502)
        }
      }
    }
  }

  return usage
}
