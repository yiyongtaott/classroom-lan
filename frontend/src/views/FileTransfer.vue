<template>
  <div class="file-transfer">
    <div class="upload-area" @dragover.prevent @drop.prevent="handleDrop">
      <input type="file" ref="fileInput" @change="handleFileSelect" hidden />
      <button class="upload-btn" @click="$refs.fileInput.click()">
        📤 选择文件上传
      </button>
      <p class="drop-hint">或直接拖拽文件到此处</p>
    </div>

    <div v-if="uploading" class="upload-progress">
      <div class="bar" :style="{ width: uploadProgress + '%' }"></div>
      <span>{{ uploadProgress }}%</span>
    </div>

    <h3>📁 文件列表</h3>
    <div class="file-list" v-if="files.length">
      <div v-for="file in files" :key="file.id" class="file-item">
        <div class="file-info">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-meta">{{ formatSize(file.size) }}</span>
        </div>
        <a :href="`/api/download/${file.id}`" class="download-btn" download>
          ⬇️ 下载
        </a>
      </div>
    </div>
    <div v-else class="empty-list">暂无文件</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const files = ref([])
const uploading = ref(false)
const uploadProgress = ref(0)
const fileInput = ref(null)

async function loadFiles() {
  try {
    const res = await fetch('/api/files')
    files.value = await res.json()
  } catch (e) {
    console.error('Failed to load file list:', e)
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(1) + ' GB'
}

async function uploadFile(file) {
  uploading.value = true
  uploadProgress.value = 0

  const formData = new FormData()
  formData.append('file', file)

  try {
    const xhr = new XMLHttpRequest()
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    }
    xhr.open('POST', '/api/upload')
    xhr.onload = () => {
      uploading.value = false
      loadFiles()
    }
    xhr.onerror = () => {
      uploading.value = false
      alert('上传失败')
    }
    xhr.send(formData)
  } catch (e) {
    uploading.value = false
    alert('上传失败')
  }
}

function handleFileSelect(e) {
  const file = e.target.files[0]
  if (file) uploadFile(file)
}

function handleDrop(e) {
  const file = e.dataTransfer.files[0]
  if (file) uploadFile(file)
}

onMounted(() => {
  loadFiles()
})
</script>

<style scoped>
.file-transfer {
  max-width: 600px; margin: 0 auto;
}
.upload-area {
  border: 2px dashed #667eea; border-radius: 16px;
  padding: 40px; text-align: center;
  background: rgba(102, 126, 234, 0.05);
}
.upload-btn {
  padding: 12px 24px; font-size: 16px;
  background: #667eea; color: white; border: none;
  border-radius: 8px; cursor: pointer;
}
.upload-btn:hover { background: #5a67d8; }
.drop-hint { margin-top: 8px; color: #718096; font-size: 14px; }

.upload-progress {
  margin-top: 16px; height: 24px; background: #edf2f7;
  border-radius: 12px; overflow: hidden; position: relative;
}
.upload-progress .bar {
  height: 100%; background: #48bb78; transition: width 0.3s;
}
.upload-progress span {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -50%); font-size: 12px; font-weight: bold;
}

h3 { margin-top: 24px; margin-bottom: 12px; font-size: 18px; color: #4a5568; }

.file-list { display: flex; flex-direction: column; gap: 8px; }
.file-item {
  display: flex; align-items: center; justify-content: space-between;
  background: white; border-radius: 8px; padding: 12px 16px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.file-info { display: flex; flex-direction: column; gap: 2px; }
.file-name { font-weight: 500; }
.file-meta { font-size: 12px; color: #718096; }
.download-btn {
  padding: 6px 12px; background: #4299e1; color: white;
  border-radius: 6px; text-decoration: none; font-size: 14px;
}
.empty-list { text-align: center; color: #a0aec0; padding: 40px; }
</style>
