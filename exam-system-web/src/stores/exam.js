import { defineStore } from 'pinia'
import { startExam as startExamApi, submitExam as submitExamApi, gradeExam as gradeExamApi } from '@/api/exam'
import { getPaperById } from '@/api/paper'

const EXAM_SESSION_KEY = 'exam-taking-state'

// 服务端时间以 @JsonFormat 固定 GMT+8 格式化输出，国内用户浏览器同为东八区，
// 去掉时区后缀按本地时间解析即可对齐
function parseServerTime(text) {
  if (!text) return null
  const ts = new Date(String(text).replace(' ', 'T')).getTime()
  return Number.isNaN(ts) ? null : ts
}

export const useExamStore = defineStore('exam', {
  state: () => ({
    currentExam: null, // { recordId, paperId, paper, studentName, answers, deadline, remainingSeconds, isSubmitted }
    windowSwitchCount: 0,
    timer: null,
  }),

  actions: {
    async startExam(paperId, studentName) {
      const record = await startExamApi({ paperId, studentName })
      const paper = await getPaperById(paperId)
      const durationMs = (paper.duration || 60) * 60 * 1000
      // 截止时间以服务端开考时间为基准：刷新重进也不会把考试时间"续命"；
      // 服务端交卷接口还有一道时长+宽限校验兜底
      const serverStart = parseServerTime(record.startTime)
      const deadline = (serverStart ?? Date.now()) + durationMs
      this.currentExam = {
        recordId: record.id,
        paperId,
        paper,
        studentName: record.studentName || studentName,
        answers: {},
        deadline,
        remainingSeconds: Math.max(0, Math.round((deadline - Date.now()) / 1000)),
        isSubmitted: false,
      }
      this.windowSwitchCount = 0
      this.persistSession()
      this.startTimer()
      return record
    },

    // 刷新恢复：从 sessionStorage 取回进行中的考试，重新拉取试卷并续上倒计时
    async tryRestore() {
      if (this.currentExam) return true
      let saved = null
      try { saved = JSON.parse(sessionStorage.getItem(EXAM_SESSION_KEY) || 'null') } catch { saved = null }
      if (!saved?.recordId || !saved?.paperId) return false
      let paper
      try { paper = await getPaperById(saved.paperId) } catch { this.clearSession(); return false }
      const deadline = saved.deadline || Date.now() + (paper.duration || 60) * 60 * 1000
      this.currentExam = {
        recordId: saved.recordId,
        paperId: saved.paperId,
        paper,
        studentName: saved.studentName || '',
        answers: saved.answers || {},
        deadline,
        remainingSeconds: Math.max(0, Math.round((deadline - Date.now()) / 1000)),
        isSubmitted: false,
      }
      this.windowSwitchCount = saved.windowSwitchCount || 0
      this.persistSession()
      this.startTimer()
      return true
    },

    // 会话级持久化：只存恢复必需的小数据，试卷本体由恢复时重新拉取；
    // 用 sessionStorage 保证换标签页/关浏览器后不会"穿越"到已失效的考试
    persistSession() {
      const exam = this.currentExam
      if (!exam || exam.isSubmitted) { this.clearSession(); return }
      sessionStorage.setItem(EXAM_SESSION_KEY, JSON.stringify({
        recordId: exam.recordId,
        paperId: exam.paperId,
        studentName: exam.studentName,
        answers: exam.answers,
        deadline: exam.deadline,
        windowSwitchCount: this.windowSwitchCount,
      }))
    },

    clearSession() {
      sessionStorage.removeItem(EXAM_SESSION_KEY)
    },

    setAnswer(questionId, answer) {
      if (this.currentExam && !this.currentExam.isSubmitted) {
        this.currentExam.answers[questionId] = answer
        this.persistSession()
      }
    },

    async submitExam() {
      if (!this.currentExam) return
      const answers = Object.entries(this.currentExam.answers).map(([questionId, userAnswer]) => ({
        questionId: Number(questionId),
        userAnswer,
      }))
      await submitExamApi(this.currentExam.recordId, answers)
      this.currentExam.isSubmitted = true
      this.stopTimer()
      this.clearSession()
    },

    async gradeExam() {
      if (!this.currentExam) return null
      return await gradeExamApi(this.currentExam.recordId)
    },

    startTimer() {
      this.stopTimer()
      this.timer = setInterval(() => {
        if (this.currentExam && !this.currentExam.isSubmitted) {
          // 剩余时间始终由截止时间倒推，避免计时器漂移
          this.currentExam.remainingSeconds = Math.max(0, Math.round((this.currentExam.deadline - Date.now()) / 1000))
          if (this.currentExam.remainingSeconds <= 0) {
            this.submitExam().catch(() => {})
          }
        }
      }, 1000)
    },

    stopTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },

    recordWindowSwitch() {
      if (this.currentExam && !this.currentExam.isSubmitted) {
        this.windowSwitchCount++
        this.persistSession()
      }
    },

    reset() {
      this.stopTimer()
      this.currentExam = null
      this.windowSwitchCount = 0
      this.clearSession()
    },
  },
})
