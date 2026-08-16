import { defineStore } from 'pinia'
import { startExam as startExamApi, submitExam as submitExamApi, gradeExam as gradeExamApi } from '@/api/exam'
import { getPaperById } from '@/api/paper'

export const useExamStore = defineStore('exam', {
  state: () => ({
    currentExam: null, // { recordId, paper, answers, startTime, remainingSeconds, isSubmitted }
    windowSwitchCount: 0,
    timer: null,
  }),

  actions: {
    async startExam(paperId, studentName) {
      const record = await startExamApi({ paperId, studentName })
      const paper = await getPaperById(paperId)
      this.currentExam = {
        recordId: record.id,
        paper,
        answers: {},
        startTime: Date.now(),
        remainingSeconds: (paper.duration || 60) * 60,
        isSubmitted: false,
      }
      this.windowSwitchCount = 0
      this.startTimer()
      return record
    },

    setAnswer(questionId, answer) {
      if (this.currentExam && !this.currentExam.isSubmitted) {
        this.currentExam.answers[questionId] = answer
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
    },

    async gradeExam() {
      if (!this.currentExam) return null
      return await gradeExamApi(this.currentExam.recordId)
    },

    startTimer() {
      this.stopTimer()
      this.timer = setInterval(() => {
        if (this.currentExam && !this.currentExam.isSubmitted) {
          this.currentExam.remainingSeconds--
          if (this.currentExam.remainingSeconds <= 0) {
            this.submitExam()
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
      }
    },

    reset() {
      this.stopTimer()
      this.currentExam = null
      this.windowSwitchCount = 0
    },
  },
})
