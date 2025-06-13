<script>
import {Button, Form, FormItem, Message, Progress, Spin, Upload} from 'view-ui-plus'

export default {
    name: "Report",
    components: {
        Upload,
        Form,
        FormItem,
        Button,
        Progress,
        Spin
    },
    data() {
        return {
            form: {
                faceImg: null,
                tongue: null,
                audio: null,
                uploading: false
            },
            uploadProgress: 0,
            reportContent: null, // 存储报告内容
            loading: false,
            showReport: false, // 控制报告显示
            rules: {
                faceImg: [
                    {required: true, message: '请上传面像图片', trigger: 'change'}
                ],
                tongue: [
                    {required: true, message: '请上传舌苔图片', trigger: 'change'}
                ],
                audio: [
                    {required: true, message: '请上传音频文件', trigger: 'change'}
                ]
            }
        }
    },
    methods: {
        beforeUpload(file, type) {
            if (type === 'faceImg' || type === 'tongue') {
                const isImage = file.type === 'image/jpeg' || file.type === 'image/png'
                if (!isImage) {
                    Message.error('只能上传JPG/PNG图片!')
                    return false
                }
            } else if (type === 'audio') {
                const isAudio = file.type === 'audio/mpeg' || file.type === 'audio/aac'
                if (!isAudio) {
                    Message.error('只能上传MP3/AAC音频文件!')
                    return false
                }
            }

            const isLt10M = file.size / 1024 / 1024 < 10
            if (!isLt10M) {
                Message.error('文件大小不能超过10MB!')
                return false
            }

            this.form[type] = file
            return false // 手动上传
        },

        async uploadMaterial() {
            try {
                Message.info('AI分析中，请耐心等待数秒...')
                this.loading = true
                this.form.uploading = true
                this.uploadProgress = 0
                this.showReport = false

                const formData = new FormData()
                formData.append('faceImg', this.form.faceImg)
                formData.append('tongue', this.form.tongue)
                formData.append('audio', this.form.audio)

                const response = await fetch('http://localhost:8080/upload', {
                    method: 'POST',
                    body: formData,
                    headers: {
                        'Accept': 'application/json'
                    }
                })

                if (!response.ok) {
                    throw new Error('上传失败')
                }

                const result = await response.json()
                this.reportContent = result.reportContent
                this.showReport = true
                Message.success('分析完成!')

            } catch (error) {
                console.error('上传出错:', error)
                Message.error('上传失败: ' + error.message)
            } finally {
                this.loading = false
                this.form.uploading = false
                this.uploadProgress = 100
            }
        },

        handleProgress(event) {
            if (event.lengthComputable) {
                this.uploadProgress = Math.round((event.loaded / event.total) * 100)
            }
        },

        resetForm() {
            this.form.faceImg = null
            this.form.tongue = null
            this.form.audio = null
            this.reportContent = null
            this.showReport = false
            this.uploadProgress = 0
        },

        downloadReport() {
            if (!this.reportContent) {
                Message.warning('没有可下载的报告内容')
                return
            }

            const blob = new Blob([this.reportContent], { type: 'text/plain' })
            const url = URL.createObjectURL(blob)
            const link = document.createElement('a')
            link.href = url
            link.download = `报告_${new Date().toLocaleDateString()}.txt`
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            URL.revokeObjectURL(url)
        }
    }
}
</script>

<template>
    <div class="report-container">
        <h1 style="text-align: center; margin-bottom: 30px;">青少年心理问题分析系统</h1>

        <Spin size="large" fix v-if="loading"></Spin>

        <div>
            <Form ref="form" :model="form" :rules="rules" label-position="top">
                <!-- 面像图片上传 -->
                <FormItem label="第一步：上传面像图片 (PNG/JPG)" prop="faceImg">
                    <Upload
                        :before-upload="(file) => beforeUpload(file, 'faceImg')"
                        accept="image/jpeg,image/png"
                        action=""
                        :show-upload-list="false"
                    >
                        <Button icon="ios-cloud-upload-outline">选择文件</Button>
                        <span v-if="form.faceImg" style="margin-left: 10px;">{{ form.faceImg.name }}</span>
                        <div v-else style="color: #999; margin-top: 5px;">请上传正面清晰的面部照片</div>
                    </Upload>
                </FormItem>

                <!-- 舌苔图片上传 -->
                <FormItem label="第二步：上传舌苔图片 (PNG/JPG)" prop="tongue">
                    <Upload
                        :before-upload="(file) => beforeUpload(file, 'tongue')"
                        accept="image/jpeg,image/png"
                        action=""
                        :show-upload-list="false"
                    >
                        <Button icon="ios-cloud-upload-outline">选择文件</Button>
                        <span v-if="form.tongue" style="margin-left: 10px;">{{ form.tongue.name }}</span>
                        <div v-else style="color: #999; margin-top: 5px;">请上传清晰的舌苔照片</div>
                    </Upload>
                </FormItem>

                <!-- 音频文件上传 -->
                <FormItem label="第三步：上传音频文件 (MP3/AAC)" prop="audio">
                    <Upload
                        :before-upload="(file) => beforeUpload(file, 'audio')"
                        accept="audio/mpeg,audio/aac"
                        action=""
                        :show-upload-list="false"
                    >
                        <Button icon="ios-cloud-upload-outline">选择文件</Button>
                        <span v-if="form.audio" style="margin-left: 10px;">{{ form.audio.name }}</span>
                        <div v-else style="color: #999; margin-top: 5px;">请上传1-3分钟的语音记录</div>
                    </Upload>
                </FormItem>

                <!-- 提交按钮 -->
                <FormItem>
                    <Button
                        type="primary"
                        @click="uploadMaterial"
                        :loading="form.uploading"
                        :disabled="!form.faceImg || !form.tongue || !form.audio || form.uploading"
                    >
                        <span v-if="form.uploading">
                            <Icon type="ios-loading" class="spin-icon"></Icon> 分析中...
                        </span>
                        <span v-else>提交分析</span>
                    </Button>
                    <Button style="margin-left: 10px;" @click="resetForm">重置</Button>
                </FormItem>
            </Form>

            <!-- 上传进度条 -->
            <Progress
                v-if="form.uploading && uploadProgress > 0"
                :percent="uploadProgress"
                :stroke-width="15"
                status="active"
                style="margin-top: 20px;"
            />

            <!-- 报告展示区域 -->
            <div v-if="showReport" class="report-display">
                <h3 style="margin: 20px 0 15px; color: #2d8cf0;">分析报告</h3>
                <div class="report-content">
                    <pre style="white-space: pre-wrap; background: #f8f8f9; padding: 20px; border-radius: 4px;">{{ reportContent }}</pre>
                </div>
                <Button
                    type="success"
                    icon="ios-download-outline"
                    @click="downloadReport"
                    style="margin-top: 15px;"
                >
                    下载报告
                </Button>
            </div>
        </div>
    </div>
</template>

<style scoped>
.report-container {
    max-width: 800px;
    margin: 0 auto;
    padding: 30px;
    background-color: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.report-display {
    margin-top: 30px;
    padding: 20px;
    border: 1px solid #e8eaec;
    border-radius: 4px;
    background-color: #fff;
}

.report-content {
    max-height: 400px;
    overflow-y: auto;
    margin-bottom: 15px;
}

.spin-icon {
    animation: spin 1s linear infinite;
    margin-right: 5px;
}

@keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}

.ivu-form-item {
    margin-bottom: 24px;
}
</style>