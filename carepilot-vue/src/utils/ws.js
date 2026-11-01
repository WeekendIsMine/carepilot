/** 一个够用的 WebSocket 客户端：自动重连 + 帧分发
 *
 * token 用 getter 传入：重连时会重新取值，配合 ensureFreshToken 做到
 * access token 过期也能无感续上，而不是拿着旧 token 一直重连失败。
 */
export class ChatSocket {
  constructor(getToken, onFrame, onStatus) {
    this.getToken = getToken
    this.onFrame = onFrame
    this.onStatus = onStatus
    this.ws = null
    this.closedByUser = false
    this.retry = 0
  }

  async connect() {
    let token = ''
    try {
      token = typeof this.getToken === 'function' ? await this.getToken() : this.getToken
    } catch (e) {
      token = ''
    }
    if (!token) {
      this.onStatus?.('error')
      return
    }
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    this.ws = new WebSocket(proto + '://' + location.host + '/ws/chat?token=' + token)
    this.ws.onopen = () => {
      this.retry = 0
      this.onStatus?.('open')
    }
    this.ws.onmessage = (ev) => {
      try {
        this.onFrame?.(JSON.parse(ev.data))
      } catch (e) {
        console.warn('bad frame', ev.data)
      }
    }
    this.ws.onclose = () => {
      this.onStatus?.('closed')
      if (!this.closedByUser && this.retry < 10) {
        this.retry += 1
        // 重连前会重新取 token（可能已经续期过）
        setTimeout(() => this.connect(), Math.min(5000, 500 * this.retry))
      }
    }
    this.ws.onerror = () => this.onStatus?.('error')
  }

  send(frame) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(frame))
      return true
    }
    return false
  }

  close() {
    this.closedByUser = true
    this.ws?.close()
  }
}
