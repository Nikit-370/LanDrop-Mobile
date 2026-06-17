package com.landrop.server

object WebPortalHtml {
    const val HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LANDrop Mobile - Local Cloud Portal</title>
    <link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;700&family=JetBrains+Mono&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0d1117;
            --surface-color: #161b22;
            --surface-border: #30363d;
            --primary: #58a6ff;
            --accent: #2ea043;
            --text-color: #c9d1d9;
            --text-heading: #f0f6fc;
            --text-muted: #8b949e;
            --shadow: rgba(0, 0, 0, 0.4);
            --gradient-accent: linear-gradient(135deg, #1f6feb 0%, #58a6ff 100%);
            --glass: rgba(22, 27, 34, 0.7);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            line-height: 1.6;
            overflow-x: hidden;
            padding-bottom: 60px;
        }

        header {
            background-color: var(--surface-color);
            border-bottom: 1px solid var(--surface-border);
            padding: 1.5rem 2rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 100;
            backdrop-filter: blur(12px);
            background: var(--glass);
        }

        .logo-container {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .logo-icon {
            background: var(--gradient-accent);
            width: 40px;
            height: 40px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #fff;
            font-weight: bold;
            font-size: 1.25rem;
            font-family: 'Space Grotesk', sans-serif;
            box-shadow: 0 4px 15px rgba(88, 166, 255, 0.3);
        }

        .logo-text h1 {
            font-family: 'Space Grotesk', sans-serif;
            font-size: 1.25rem;
            color: var(--text-heading);
            font-weight: 700;
        }

        .logo-text p {
            font-size: 0.8rem;
            color: var(--text-muted);
        }

        .device-info {
            background-color: hsla(215, 15%, 25%, 0.4);
            padding: 0.4rem 0.8rem;
            border-radius: 20px;
            font-size: 0.85rem;
            border: 1px solid var(--surface-border);
            font-family: 'JetBrains Mono', monospace;
            color: var(--primary);
        }

        .container {
            max-width: 1200px;
            margin: 2rem auto;
            padding: 0 1.5rem;
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 2rem;
        }

        @media (max-width: 900px) {
            .container {
                grid-template-columns: 1fr;
            }
        }

        .card {
            background-color: var(--surface-color);
            border: 1px solid var(--surface-border);
            border-radius: 12px;
            padding: 1.5rem;
            box-shadow: 0 4px 20px var(--shadow);
            margin-bottom: 2rem;
            position: relative;
        }

        .card-title {
            font-family: 'Space Grotesk', sans-serif;
            font-size: 1.15rem;
            color: var(--text-heading);
            margin-bottom: 1.25rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-weight: 600;
        }

        .search-container {
            position: relative;
            margin-bottom: 1.5rem;
        }

        .search-input {
            width: 100%;
            background-color: #0d1117;
            border: 1px solid var(--surface-border);
            border-radius: 8px;
            padding: 0.75rem 1rem 0.75rem 2.5rem;
            color: var(--text-color);
            font-size: 0.9rem;
            outline: none;
            transition: border-color 0.2s;
        }

        .search-input:focus {
            border-color: var(--primary);
        }

        .search-icon {
            position: absolute;
            left: 0.75rem;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            pointer-events: none;
        }

        /* Tabs Navigation */
        .tabs-header {
            display: flex;
            gap: 0.5rem;
            border-bottom: 1px solid var(--surface-border);
            margin-bottom: 1.5rem;
            padding-bottom: 0px;
        }

        .tab-btn {
            background: none;
            border: none;
            border-bottom: 2px solid transparent;
            color: var(--text-muted);
            padding: 0.75rem 1.25rem;
            font-size: 0.95rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            gap: 0.4rem;
        }

        .tab-btn:hover {
            color: var(--text-heading);
            border-bottom-color: var(--surface-border);
        }

        .tab-btn.active {
            color: var(--primary);
            border-bottom-color: var(--primary);
        }

        .tab-badge {
            background-color: #21262d;
            color: var(--text-muted);
            font-size: 0.75rem;
            font-weight: 500;
            padding: 0.15rem 0.5rem;
            border-radius: 10px;
            margin-left: 0.25rem;
            border: 1px solid var(--surface-border);
        }

        .tab-btn.active .tab-badge {
            background-color: rgba(88, 166, 255, 0.15);
            color: var(--primary);
            border-color: rgba(88, 166, 255, 0.3);
        }

        .file-list {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
            max-height: 600px;
            overflow-y: auto;
            padding-right: 5px;
        }

        .file-item {
            background-color: #1cc2128;
            background-color: #1c2128;
            border: 1px solid var(--surface-border);
            border-radius: 8px;
            padding: 0.75rem 1rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
            transition: transform 0.2s, border-color 0.2s;
            cursor: pointer;
        }

        .file-item:hover {
            transform: translateY(-2px);
            border-color: var(--primary);
        }

        .file-info {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            overflow: hidden;
            flex-grow: 1;
            margin-right: 1rem;
        }

        .file-icon {
            font-size: 1.5rem;
            min-width: 2.22rem;
            height: 2.22rem;
            border-radius: 6px;
            background-color: #21262d;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .file-details {
            overflow: hidden;
        }

        .file-name {
            font-weight: 500;
            color: var(--text-heading);
            font-size: 0.95rem;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .file-meta {
            font-size: 0.8rem;
            color: var(--text-muted);
            display: flex;
            flex-wrap: wrap;
            gap: 0.75rem;
            align-items: center;
        }

        .badge {
            font-size: 0.7rem;
            font-weight: 600;
            padding: 0.1rem 0.4rem;
            border-radius: 4px;
            text-transform: uppercase;
        }
        .badge-success { background: rgba(46, 160, 67, 0.15); color: #2ea043; border: 1px solid rgba(46, 160, 67, 0.3); }
        .badge-transferring { background: rgba(88, 166, 255, 0.15); color: var(--primary); border: 1px solid rgba(88, 166, 255, 0.3); }
        .badge-failed { background: rgba(218, 54, 51, 0.15); color: #ff7b72; border: 1px solid rgba(218, 54, 51, 0.3); }
        .badge-deleted { background: rgba(139, 148, 158, 0.15); color: var(--text-muted); border: 1px solid rgba(139, 148, 158, 0.3); }

        .btn {
            background: var(--gradient-accent);
            border: none;
            border-radius: 6px;
            color: #fff;
            padding: 0.5rem 1rem;
            font-size: 0.85rem;
            font-weight: 500;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 0.4rem;
            transition: opacity 0.2s, transform 0.1s;
            text-decoration: none;
        }

        .btn:hover {
            opacity: 0.9;
        }

        .btn:active {
            transform: scale(0.97);
        }

        .btn-sec {
            background-color: #21262d;
            border: 1px solid var(--surface-border);
            color: var(--text-color);
        }

        .btn-sec:hover {
            background-color: #30363d;
            color: var(--text-heading);
        }

        .btn-danger {
            background: linear-gradient(135deg, #ff7b72 0%, #da3633 100%);
            border-color: #da3633;
        }

        /* Upload Area */
        .dropzone {
            border: 2px dashed var(--surface-border);
            border-radius: 12px;
            padding: 2.5rem 1.5rem;
            text-align: center;
            cursor: pointer;
            transition: border-color 0.25s, background-color 0.25s;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 0.75rem;
        }

        .dropzone.dragover {
            border-color: var(--primary);
            background-color: rgba(88, 166, 255, 0.05);
        }

        .dropzone-icon {
            font-size: 2.5rem;
            color: var(--primary);
        }

        .upload-progress-list {
            margin-top: 1.5rem;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .progress-item {
            background-color: #141920;
            border: 1px solid var(--surface-border);
            border-radius: 8px;
            padding: 0.75rem;
        }

        .progress-header {
            display: flex;
            justify-content: space-between;
            font-size: 0.8rem;
            margin-bottom: 0.4rem;
        }

        .progress-bar-container {
            background-color: #21262d;
            height: 6px;
            border-radius: 3px;
            overflow: hidden;
            position: relative;
        }

        .progress-bar {
            background: var(--gradient-accent);
            height: 100%;
            width: 0%;
            transition: width 0.15s;
        }

        /* Modals & Media Previews */
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0, 0, 0, 0.85);
            z-index: 1000;
            justify-content: center;
            align-items: center;
            backdrop-filter: blur(8px);
        }

        .modal-content {
            background-color: var(--surface-color);
            border: 1px solid var(--surface-border);
            border-radius: 12px;
            max-width: 800px;
            width: 90%;
            max-height: 85vh;
            display: flex;
            flex-direction: column;
            box-shadow: 0 10px 40px rgba(0,0,0,0.6);
            overflow: hidden;
        }

        .modal-header {
            padding: 1rem 1.5rem;
            border-bottom: 1px solid var(--surface-border);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-title {
            font-family: 'Space Grotesk', sans-serif;
            font-weight: 600;
            color: var(--text-heading);
            font-size: 1.1rem;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
            padding-right: 1.5rem;
        }

        .modal-close {
            background: none;
            border: none;
            color: var(--text-muted);
            font-size: 1.5rem;
            cursor: pointer;
            transition: color 0.1s;
        }

        .modal-close:hover {
            color: var(--text-heading);
        }

        .modal-body {
            padding: 1.5rem;
            display: flex;
            justify-content: center;
            align-items: center;
            overflow: auto;
            max-height: 65vh;
        }

        .modal-body img {
            max-width: 100%;
            max-height: 60vh;
            object-fit: contain;
            border-radius: 6px;
        }

        .modal-body video {
            width: 100%;
            max-height: 60vh;
            border-radius: 6px;
            outline: none;
        }

        .modal-body audio {
            width: 100%;
            outline: none;
        }

        /* Password Overlay */
        .password-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: var(--bg-color);
            z-index: 2000;
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .password-card {
            background-color: var(--surface-color);
            border: 1px solid var(--surface-border);
            padding: 2.5rem;
            border-radius: 12px;
            width: 100%;
            max-width: 400px;
            text-align: center;
            box-shadow: 0 8px 32px var(--shadow);
        }

        .password-card h2 {
            font-family: 'Space Grotesk', sans-serif;
            color: var(--text-heading);
            margin-bottom: 0.5rem;
        }

        .password-card p {
            font-size: 0.85rem;
            color: var(--text-muted);
            margin-bottom: 1.5rem;
        }

        .password-input {
            width: 100%;
            background-color: #0d1117;
            border: 1px solid var(--surface-border);
            border-radius: 8px;
            padding: 0.75rem 1rem;
            color: #fff;
            text-align: center;
            font-size: 1.25rem;
            letter-spacing: 0.25rem;
            outline: none;
            margin-bottom: 1rem;
        }

        .password-input:disabled {
            background-color: #161b22;
            cursor: not-allowed;
            opacity: 0.6;
        }

        .password-input:focus {
            border-color: var(--primary);
        }

        .error-msg {
            color: #ff7b72;
            font-size: 0.85rem;
            margin-top: 0.5rem;
            display: none;
        }

        .empty-state {
            text-align: center;
            padding: 3.5rem 1.5rem;
            color: var(--text-muted);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 1rem;
        }

        .empty-state-icon {
            font-size: 3rem;
            opacity: 0.4;
        }

        /* Scrollbar aesthetics */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: #0d1117;
        }
        ::-webkit-scrollbar-thumb {
            background: #30363d;
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #8b949e;
        }

        /* Pagination Styling */
        .pagination-container {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 1.25rem;
            padding-top: 1rem;
            border-top: 1px solid var(--surface-border);
        }

        .pagination-btn {
            background-color: #21262d;
            border: 1px solid var(--surface-border);
            color: var(--text-color);
            padding: 0.4rem 0.8rem;
            font-size: 0.85rem;
            font-weight: 600;
            border-radius: 6px;
            cursor: pointer;
            transition: background-color 0.2s, color 0.2s;
        }

        .pagination-btn:hover:not(:disabled) {
            background-color: var(--primary);
            color: #fff;
            border-color: var(--primary);
        }

        .pagination-btn:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }

        .pagination-info {
            font-size: 0.85rem;
            color: var(--text-muted);
            font-weight: 500;
        }
    </style>
</head>
<body>

    <div id="password-overlay" class="password-overlay" style="display: none;">
        <div class="password-card">
            <div class="logo-icon" style="margin: 0 auto 1rem auto;">LA</div>
            <h2>Password Protected</h2>
            <p>Please enter the Access PIN shown on the mobile app screen to connect.</p>
            <input type="password" id="pin-input" class="password-input" placeholder="Enter PIN (4–8 digits)" maxlength="8" inputmode="numeric" pattern="[0-9]*">
            <button id="pin-submit" class="btn" style="width: 100%; justify-content: center;">Authenticate</button>
            <div id="error-msg" class="error-msg">PIN must contain 4–8 digits.</div>
        </div>
    </div>

    <header>
        <div class="logo-container">
            <div class="logo-icon">LA</div>
            <div class="logo-text">
                <h1>LANDrop Mobile</h1>
                <p>Offline Local File Server & Streaming</p>
            </div>
        </div>
        <div id="device-info" class="device-info">Connecting...</div>
    </header>

    <div class="container">
        <!-- Main Column: File Explorer Tabs & Content -->
        <div>
            <div class="card">
                <div class="tabs-header">
                    <button id="tab-btn-shared" class="tab-btn active" onclick="switchTab('shared')">
                        <span>📦</span> Shared Files <span id="badge-shared" class="tab-badge">0</span>
                    </button>
                    <button id="tab-btn-received" class="tab-btn" onclick="switchTab('received')">
                        <span>📥</span> Received Files <span id="badge-received" class="tab-badge">0</span>
                    </button>
                    <button id="tab-btn-history" class="tab-btn" onclick="switchTab('history')">
                        <span>⏳</span> History <span id="badge-history" class="tab-badge">0</span>
                    </button>
                </div>

                <div class="search-container">
                    <span class="search-icon">🔍</span>
                    <input type="text" id="search-input" class="search-input" placeholder="Search shared files...">
                </div>

                <!-- Tab Contents -->
                <div id="tab-shared" class="tab-content">
                    <div id="shared-file-list" class="file-list">
                        <div class="empty-state">
                            <div class="empty-state-icon">📡</div>
                            <p>No files are currently selected for sharing on the phone. Choose files in the LANDrop app to view them here.</p>
                        </div>
                    </div>
                </div>

                <div id="tab-received" class="tab-content" style="display: none;">
                    <div id="received-file-list" class="file-list">
                        <div class="empty-state">
                            <div class="empty-state-icon">🗑️</div>
                            <p>No uploaded files exist yet. Send files from your device using the lateral upload zone.</p>
                        </div>
                    </div>
                </div>

                <div id="tab-history" class="tab-content" style="display: none;">
                    <div id="history-list" class="file-list">
                        <div class="empty-state">
                            <div class="empty-state-icon">📄</div>
                            <p>No historical file transfer operations have been logged yet.</p>
                        </div>
                    </div>
                </div>

                <!-- Shared Unified Pagination Control -->
                <div id="pagination-controls" class="pagination-container" style="display: none;">
                    <button id="prev-btn" class="pagination-btn" onclick="changePage(-1)">Previous</button>
                    <span id="page-info" class="pagination-info">1 / 1</span>
                    <button id="next-btn" class="pagination-btn" onclick="changePage(1)">Next</button>
                </div>
            </div>
        </div>

        <!-- Sidebar: Upload Zone -->
        <div>
            <div class="card">
                <div class="card-title">
                    <span>📤</span> Upload to Phone
                </div>
                <div class="dropzone" id="dropzone">
                    <div class="dropzone-icon">📥</div>
                    <div style="font-weight: 600; color: var(--text-heading);">Drag & Drop Files Here</div>
                    <div style="font-size: 0.8rem; color: var(--text-muted);">or click to browse local files</div>
                    <input type="file" id="file-input" style="display: none;" multiple>
                </div>

                <div id="upload-progress-list" class="upload-progress-list"></div>
            </div>
        </div>
    </div>

    <!-- Media Preview Modals -->
    <div id="preview-modal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <div id="modal-title" class="modal-title">File Preview</div>
                <button id="modal-close" class="modal-close">&times;</button>
            </div>
            <div id="modal-body" class="modal-body">
                <!-- Content injected dynamically -->
            </div>
        </div>
    </div>

    <!-- Custom Confirmation Modal -->
    <div id="confirm-modal" class="modal">
        <div class="modal-content" style="max-width: 420px;">
            <div class="modal-header">
                <div id="confirm-title" class="modal-title">Delete File</div>
                <button class="modal-close" onclick="closeConfirmModal()">&times;</button>
            </div>
            <div id="confirm-body" class="modal-body" style="flex-direction: column; gap: 1.5rem; text-align: center; padding: 2rem 1.5rem;">
                <p id="confirm-message" style="color: var(--text-color); font-weight: 500; font-size: 1rem;"></p>
                <div style="display: flex; gap: 1rem; width: 100%; justify-content: center;">
                    <button class="btn btn-sec" onclick="closeConfirmModal()" style="min-width: 100px; justify-content: center;">Cancel</button>
                    <button id="confirm-ok-btn" class="btn btn-danger" style="min-width: 100px; justify-content: center;">Delete</button>
                </div>
            </div>
        </div>
    </div>

    <script>
        let filesData = [];
        let receivedData = [];
        let historyData = [];
        let pin = localStorage.getItem('lan_sharing_pin') || "";
        if (pin) {
            const isValid = /^[0-9]{4,8}$/.test(pin);
            if (!isValid) {
                localStorage.removeItem('lan_sharing_pin');
                pin = "";
            }
        }
        let isAuthRequired = false;
        let currentPage = 0;
        const itemsPerPage = 6;
        let activeTab = 'shared';

        // Elements
        const sharedFileListEl = document.getElementById('shared-file-list');
        const receivedFileListEl = document.getElementById('received-file-list');
        const historyListEl = document.getElementById('history-list');
        const searchInput = document.getElementById('search-input');
        const dropzone = document.getElementById('dropzone');
        const fileInput = document.getElementById('file-input');
        const uploadProgressList = document.getElementById('upload-progress-list');
        const previewModal = document.getElementById('preview-modal');
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');
        const modalClose = document.getElementById('modal-close');
        const deviceInfoEl = document.getElementById('device-info');
        const passwordOverlay = document.getElementById('password-overlay');
        const pinInput = document.getElementById('pin-input');
        const pinSubmit = document.getElementById('pin-submit');
        const errorMsg = document.getElementById('error-msg');

        // Confirm Modal Elements
        const confirmModal = document.getElementById('confirm-modal');
        const confirmMessage = document.getElementById('confirm-message');
        const confirmOkBtn = document.getElementById('confirm-ok-btn');
        let confirmAction = null;

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, '&amp;')
                      .replace(/</g, '&lt;')
                      .replace(/>/g, '&gt;')
                      .replace(/"/g, '&quot;')
                      .replace(/'/g, '&#039;');
        }

        // Initial setup
        checkServerState();
        setInterval(checkServerState, 3000); // Poll status every 3s

        function getHeaders() {
            const headers = {};
            if (pin) {
                headers['x-lan-password'] = pin;
            }
            return headers;
        }

        async function checkServerState() {
            try {
                const response = await fetch('/api/status', { headers: getHeaders() });
                if (response.status === 401) {
                    isAuthRequired = true;
                    passwordOverlay.style.display = 'flex';
                    const hadPin = !!pin;
                    if (pin) {
                        localStorage.removeItem('lan_sharing_pin');
                        pin = "";
                    }
                    try {
                        const errData = await response.json();
                        if (errData) {
                            if (errData.locked && errData.remainingMs) {
                                startLockoutCountdown(errData.remainingMs);
                            } else {
                                if (hadPin && errData.error) {
                                    errorMsg.innerText = errData.error;
                                    errorMsg.style.display = 'block';
                                }
                                clearLockoutUi();
                            }
                        }
                    } catch (jsonErr) {}
                    return;
                }

                if (isAuthRequired) {
                    passwordOverlay.style.display = 'none';
                    isAuthRequired = false;
                    clearLockoutUi();
                }

                const data = await response.json();
                deviceInfoEl.innerText = data.device_name || "LANDrop Mobile Server";

                let changed = false;

                if (JSON.stringify(filesData) !== JSON.stringify(data.files)) {
                    filesData = data.files || [];
                    changed = true;
                }
                if (JSON.stringify(receivedData) !== JSON.stringify(data.received)) {
                    receivedData = data.received || [];
                    changed = true;
                }
                if (JSON.stringify(historyData) !== JSON.stringify(data.history)) {
                    historyData = data.history || [];
                    changed = true;
                }

                document.getElementById('badge-shared').innerText = filesData.length;
                document.getElementById('badge-received').innerText = receivedData.length;
                document.getElementById('badge-history').innerText = historyData.length;

                if (changed) {
                    renderActiveList();
                }
            } catch (e) {
                deviceInfoEl.innerText = "Offline/Disconnect";
                console.error("Failed to connect to server", e);
            }
        }

        function switchTab(tabId) {
            activeTab = tabId;
            currentPage = 0;

            // Update Tab Button styles
            document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
            document.getElementById('tab-btn-' + tabId).classList.add('active');

            // Update Tab Content display
            document.querySelectorAll('.tab-content').forEach(section => section.style.display = 'none');
            document.getElementById('tab-' + tabId).style.display = 'block';

            // Change search placeholder depending on tab
            if (tabId === 'shared') {
                searchInput.placeholder = "Search shared files...";
            } else if (tabId === 'received') {
                searchInput.placeholder = "Search received files...";
            } else {
                searchInput.placeholder = "Search history...";
            }

            renderActiveList();
        }

        function renderActiveList() {
            const filter = searchInput.value.toLowerCase();
            let rawData = [];
            let listEl = null;
            let emptyIcon = '🔍';
            let emptyText = "No records found.";

            if (activeTab === 'shared') {
                rawData = filesData;
                listEl = sharedFileListEl;
                emptyIcon = '📡';
                emptyText = filesData.length === 0 ? 
                    "No files are currently selected for sharing on the phone. Choose files in the LANDrop app." : 
                    "No shared files match your search criteria.";
            } else if (activeTab === 'received') {
                rawData = receivedData;
                listEl = receivedFileListEl;
                emptyIcon = '🗑️';
                emptyText = receivedData.length === 0 ? 
                    "No uploaded files exist yet. Send files from your device using the lateral upload zone." : 
                    "No received files match your search criteria.";
            } else {
                rawData = historyData;
                listEl = historyListEl;
                emptyIcon = '📄';
                emptyText = historyData.length === 0 ? 
                    "No historical file transfer operations have been logged yet." : 
                    "No history entries match your search criteria.";
            }

            const filtered = rawData.filter(item => {
                const searchString = item.name || item.fileName || "";
                return searchString.toLowerCase().includes(filter);
            });

            const totalPages = Math.ceil(filtered.length / itemsPerPage);
            if (currentPage >= totalPages) {
                currentPage = Math.max(0, totalPages - 1);
            }

            if (filtered.length === 0) {
                listEl.innerHTML = '<div class="empty-state">' +
                    '<div class="empty-state-icon">' + emptyIcon + '</div>' +
                    '<p>' + emptyText + '</p>' +
                '</div>';
                document.getElementById('pagination-controls').style.display = 'none';
                return;
            }

            const startIndex = currentPage * itemsPerPage;
            const endIndex = Math.min(startIndex + itemsPerPage, filtered.length);
            const pageItems = filtered.slice(startIndex, endIndex);

            listEl.innerHTML = '';
            pageItems.forEach(item => {
                const itemDiv = document.createElement('div');
                itemDiv.className = 'file-item';

                if (activeTab === 'shared') {
                    renderSharedItem(itemDiv, item);
                } else if (activeTab === 'received') {
                    renderReceivedItem(itemDiv, item);
                } else {
                    renderHistoryItem(itemDiv, item);
                }
                
                listEl.appendChild(itemDiv);
            });

            if (totalPages > 1) {
                document.getElementById('pagination-controls').style.display = 'flex';
                document.getElementById('prev-btn').disabled = currentPage === 0;
                document.getElementById('next-btn').disabled = currentPage === totalPages - 1;
                document.getElementById('page-info').innerText = (currentPage + 1) + ' / ' + totalPages;
            } else {
                document.getElementById('pagination-controls').style.display = 'none';
            }
        }

        // 1. Shared Item Renderer
        function renderSharedItem(el, file) {
            const ext = file.name.split('.').pop().toLowerCase();
            const icon = getFileIcon(ext);
            const isMedia = isPreviewSupported(ext);
            const accessUrl = "/download?id=" + file.id + (pin ? "&pin=" + encodeURIComponent(pin) : "");

            el.innerHTML = '<div class="file-info" onclick="handleFileClick(\'' + file.id + '\', false)">' +
                    '<div class="file-icon">' + icon + '</div>' +
                    '<div class="file-details">' +
                        '<div class="file-name" title="' + escapeHtml(file.name) + '">' + escapeHtml(file.name) + '</div>' +
                        '<div class="file-meta">' +
                            '<span>' + formatBytes(file.size) + '</span>' +
                            (isMedia ? '<span style="color:var(--primary)">• Preview available</span>' : '') +
                        '</div>' +
                    '</div>' +
                '</div>' +
                '<div class="file-actions" style="display: flex; gap: 0.5rem; align-items: center;">' +
                    '<a href="' + accessUrl + '" download="' + escapeHtml(file.name) + '" onclick="event.stopPropagation()" class="btn btn-sec">' +
                        'Download' +
                    '</a>' +
                    '<button onclick="event.stopPropagation(); confirmUnshareFile(\'' + file.id + '\', \'' + escapeHtml(file.name).replace(/'/g, "\\'") + '\')" class="btn btn-danger" style="width: auto; font-weight: bold;">' +
                        'Unshare' +
                    '</button>' +
                '</div>';
        }

        // 2. Received Item Renderer
        function renderReceivedItem(el, file) {
            const ext = file.name.split('.').pop().toLowerCase();
            const icon = getFileIcon(ext);
            const isMedia = isPreviewSupported(ext);
            const formattedTime = formatTime(file.timestamp);
            const accessUrl = "/download?id=" + file.id + "&type=received" + (pin ? "&pin=" + encodeURIComponent(pin) : "");

            el.innerHTML = '<div class="file-info" onclick="handleFileClick(\'' + file.id + '\', true)">' +
                    '<div class="file-icon">' + icon + '</div>' +
                    '<div class="file-details">' +
                        '<div class="file-name" title="' + escapeHtml(file.name) + '">' + escapeHtml(file.name) + '</div>' +
                        '<div class="file-meta">' +
                            '<span>' + formatBytes(file.size) + '</span>' +
                            '<span style="color: var(--text-muted);">' + formattedTime + '</span>' +
                            (isMedia ? '<span style="color:var(--accent)">• Stream / Preview</span>' : '') +
                        '</div>' +
                    '</div>' +
                '</div>' +
                '<div class="file-actions" style="display: flex; gap: 0.5rem; align-items: center;">' +
                    '<a href="' + accessUrl + '" download="' + escapeHtml(file.name) + '" onclick="event.stopPropagation()" class="btn btn-sec">' +
                        'Download' +
                    '</a>' +
                    '<button onclick="event.stopPropagation(); deleteReceivedFile(\'' + file.id + '\', \'' + escapeHtml(file.name).replace(/'/g, "\\'") + '\')" class="btn btn-danger" style="width: auto; font-weight: bold;">' +
                        'Delete' +
                    '</button>' +
                '</div>';
        }

        // 3. History Item Renderer
        function renderHistoryItem(el, entry) {
            const ext = entry.name.split('.').pop().toLowerCase();
            const icon = getFileIcon(ext);
            const directionIcon = entry.isUpload ? '📥 Upload' : '📤 Download';
            const formattedTime = formatTime(entry.timestamp);
            
            let badgeClass = 'badge-failed';
            if (entry.status === 'SUCCESS') badgeClass = 'badge-success';
            else if (entry.status === 'TRANSFERRING' || entry.status === 'PROCESSING') badgeClass = 'badge-transferring';
            else if (entry.status === 'DELETED') badgeClass = 'badge-deleted';

            el.innerHTML = '<div class="file-info" style="cursor: default;">' +
                    '<div class="file-icon">' + icon + '</div>' +
                    '<div class="file-details">' +
                        '<div class="file-name" title="' + escapeHtml(entry.name) + '">' + escapeHtml(entry.name) + '</div>' +
                        '<div class="file-meta">' +
                            '<span class="badge ' + badgeClass + '">' + entry.status + '</span>' +
                            '<span>' + formatBytes(entry.size) + '</span>' +
                            '<span>' + directionIcon + '</span>' +
                            '<span>' + formattedTime + '</span>' +
                            '<span style="color: var(--primary); font-family: monospace; font-size: 0.75rem;">' + escapeHtml(entry.remoteDevice) + '</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
        }

        function getFileIcon(ext) {
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext)) return '🖼️';
            if (['mp4', 'mkv', 'webm', 'mov', 'avi'].includes(ext)) return '🎬';
            if (['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(ext)) return '🎵';
            if (ext === 'pdf') return '📕';
            if (['txt', 'log', 'md', 'json', 'js', 'html', 'css', 'xml', 'csv'].includes(ext)) return '📄';
            if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '📦';
            return '⚙️';
        }

        function isPreviewSupported(ext) {
            return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'mp4', 'mkv', 'webm', 'mov', 'avi', 'mp3', 'wav', 'ogg', 'm4a', 'flac', 'pdf', 'txt', 'log', 'md', 'json', 'js', 'html', 'css', 'xml', 'csv'].includes(ext);
        }

        function formatTime(timestamp) {
            if (!timestamp) return '';
            const d = new Date(timestamp);
            return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }

        function handleFileClick(id, isReceived) {
            const dataList = isReceived ? receivedData : filesData;
            const file = dataList.find(f => f.id === id);
            if (!file) return;

            modalTitle.innerText = file.name;
            const urlTypeParam = isReceived ? "&type=received" : "";
            const accessUrl = "/download?id=" + id + urlTypeParam + (pin ? "&pin=" + encodeURIComponent(pin) : "");

            const ext = file.name.split('.').pop().toLowerCase();
            const icon = getFileIcon(ext);
            
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext)) {
                modalBody.innerHTML = '<img src="' + accessUrl + '" alt="' + escapeHtml(file.name) + '">';
                previewModal.style.display = 'flex';
            } else if (['mp4', 'mkv', 'webm', 'mov', 'avi'].includes(ext)) {
                modalBody.innerHTML = '<video src="' + accessUrl + '" controls autoplay style="width:100%; border-radius:8px;"></video>';
                previewModal.style.display = 'flex';
            } else if (['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(ext)) {
                modalBody.innerHTML = '<div style="width:100%; text-align:center; padding:2rem;"><audio src="' + accessUrl + '" controls autoplay style="width:100%;"></audio></div>';
                previewModal.style.display = 'flex';
            } else if (ext === 'pdf') {
                modalBody.innerHTML = '<iframe src="' + accessUrl + '" style="width:100%; height:65vh; border:none; background:white; border-radius:8px;"></iframe>';
                previewModal.style.display = 'flex';
            } else if (['txt', 'log', 'md', 'json', 'js', 'html', 'css', 'ini', 'xml', 'csv'].includes(ext)) {
                modalBody.innerHTML = '<div style="padding:1rem; text-align:center; color:var(--text-muted);">Loading text content...</div>';
                previewModal.style.display = 'flex';
                fetch(accessUrl)
                    .then(response => response.text())
                    .then(text => {
                        modalBody.innerHTML = '<pre style="width:100%; max-height:60vh; overflow:auto; background:#0d1117; padding:1.25rem; border-radius:8px; text-align:left; font-family:\'JetBrains Mono\', monospace; font-size:0.85rem; color:#f0f6fc; border:1px solid var(--surface-border); white-space:pre-wrap; word-break:break-all;">' + escapeHtml(text) + '</pre>';
                    })
                    .catch(err => {
                        modalBody.innerHTML = '<div style="padding:1rem; text-align:center; color:#ff7b72;">Failed to load text preview. Keep in mind some private browser policies might block stream reads.</div>';
                    });
            } else {
                // For unsupported files, show a premium download preview page
                modalBody.innerHTML = '<div style="display:flex; flex-direction:column; align-items:center; gap:1.25rem; padding:2rem; width:100%; text-align:center;">' +
                    '<div style="font-size:4rem;">' + icon + '</div>' +
                    '<div style="font-family:\'Space Grotesk\', sans-serif; font-size:1.15rem; font-weight:600; color:var(--text-heading); word-break:break-all;">' + escapeHtml(file.name) + '</div>' +
                    '<div style="font-size:0.9rem; color:var(--text-muted);">' + formatBytes(file.size) + '</div>' +
                    '<div style="font-size:0.85rem; color:var(--text-muted); max-width:320px;">Direct preview is not supported for this file type. You can download the file to view it on your system.</div>' +
                    '<a href="' + accessUrl + '" class="btn" download="' + escapeHtml(file.name) + '" style="margin-top:0.5rem; padding:0.6rem 1.5rem; justify-content:center; min-width:180px;">' +
                        '📥 Download File' +
                    '</a>' +
                '</div>';
                previewModal.style.display = 'flex';
            }
        }

        // Custom Confirmation dialog binders
        function showConfirmModal(title, message, btnText, btnClass, action) {
            document.getElementById('confirm-title').innerText = title;
            confirmMessage.innerText = message;
            confirmOkBtn.innerText = btnText;
            confirmOkBtn.className = 'btn ' + btnClass;
            confirmAction = action;
            confirmModal.style.display = 'flex';
        }

        function closeConfirmModal() {
            confirmModal.style.display = 'none';
            confirmAction = null;
        }

        confirmOkBtn.addEventListener('click', () => {
            if (confirmAction) {
                confirmAction();
            }
            closeConfirmModal();
        });

        // ACTIONS execution
        async function confirmUnshareFile(id, name) {
            showConfirmModal("Stop Sharing?", "Stop sharing this file?", "Unshare", "btn-danger", async () => {
                try {
                    const deleteUrl = "/api/delete?id=" + id + (pin ? "&pin=" + encodeURIComponent(pin) : "");
                    const response = await fetch(deleteUrl, { 
                        method: 'POST',
                        headers: getHeaders()
                    });
                    if (response.ok) {
                        checkServerState();
                    } else {
                        alert("Failed to unshare file.");
                    }
                } catch(e) {
                    console.error(e);
                    alert("Error unsharing file.");
                }
            });
        }

        async function deleteReceivedFile(id, name) {
            showConfirmModal("Delete File", "Delete uploaded file '" + name + "'?", "Delete", "btn-danger", async () => {
                try {
                    const deleteUrl = "/api/delete?id=" + id + "&type=received" + (pin ? "&pin=" + encodeURIComponent(pin) : "");
                    const response = await fetch(deleteUrl, { 
                        method: 'POST',
                        headers: getHeaders()
                    });
                    if (response.ok) {
                        checkServerState();
                    } else {
                        alert("Failed to delete file.");
                    }
                } catch(e) {
                    console.error(e);
                    alert("Error deleting file.");
                }
            });
        }

        modalClose.addEventListener('click', () => {
            modalBody.innerHTML = '';
            previewModal.style.display = 'none';
        });

        window.onclick = function(event) {
            if (event.target == previewModal) {
                modalBody.innerHTML = '';
                previewModal.style.display = 'none';
            }
            if (event.target == confirmModal) {
                closeConfirmModal();
            }
        }

        function changePage(direction) {
            currentPage += direction;
            renderActiveList();
        }

        searchInput.addEventListener('input', () => {
            currentPage = 0;
            renderActiveList();
        });

        dropzone.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', () => {
            uploadFiles(fileInput.files);
        });

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        });

        dropzone.addEventListener('dragleave', () => {
            dropzone.classList.remove('dragover');
        });

        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            uploadFiles(e.dataTransfer.files);
        });

        function uploadFiles(files) {
            if (files.length === 0) return;
            Array.from(files).forEach(file => {
                createUploadProgress(file);
            });
        }

        function createUploadProgress(file) {
            const uploadId = 'up-' + Math.random().toString(36).substring(2, 9);
            const item = document.createElement('div');
            item.className = 'progress-item';
            item.id = uploadId;
            item.innerHTML = '<div class="progress-header">' +
                    '<span style="font-weight: 500; font-size: 0.85rem; max-width: 70%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">' + escapeHtml(file.name) + '</span>' +
                    '<span id="' + uploadId + '-percent">0%</span>' +
                '</div>' +
                '<div class="progress-bar-container">' +
                    '<div class="progress-bar" id="' + uploadId + '-bar"></div>' +
                '</div>';
            
            uploadProgressList.prepend(item);

            const xhr = new XMLHttpRequest();
            const uploadUrl = "/api/upload?name=" + encodeURIComponent(file.name) + "&size=" + file.size + (pin ? "&pin=" + encodeURIComponent(pin) : "");
            
            xhr.open('POST', uploadUrl, true);
            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    document.getElementById(uploadId + '-bar').style.width = percent + '%';
                    document.getElementById(uploadId + '-percent').innerText = percent + '%';
                }
            });

            xhr.onload = function() {
                if (xhr.status === 200) {
                    document.getElementById(uploadId + '-bar').style.backgroundColor = 'var(--accent)';
                    document.getElementById(uploadId + '-percent').innerText = 'Completed ✓';
                    document.getElementById(uploadId + '-percent').style.color = 'var(--accent)';
                    setTimeout(() => item.remove(), 4000);
                    checkServerState();
                } else if (xhr.status === 401) {
                    document.getElementById(uploadId + '-percent').innerText = 'Auth Failed';
                    document.getElementById(uploadId + '-percent').style.color = '#ff7b72';
                } else {
                    document.getElementById(uploadId + '-percent').innerText = 'Failed ✗';
                    document.getElementById(uploadId + '-percent').style.color = '#ff7b72';
                }
            };

            xhr.onerror = function() {
                document.getElementById(uploadId + '-percent').innerText = 'Failed ✗';
                document.getElementById(uploadId + '-percent').style.color = '#ff7b72';
            };

            xhr.send(file);
        }

        let lockoutTimer = null;
        let localRemainingSecs = 0;

        function updateLockoutUi() {
            if (localRemainingSecs > 0) {
                errorMsg.innerText = "Locked out. Try again in " + localRemainingSecs + " seconds.";
                errorMsg.style.display = 'block';
                pinInput.disabled = true;
                pinSubmit.disabled = true;
                pinSubmit.style.opacity = '0.5';
                pinSubmit.style.cursor = 'not-allowed';
            } else {
                clearLockoutUi();
            }
        }

        function clearLockoutUi() {
            if (lockoutTimer) {
                clearInterval(lockoutTimer);
                lockoutTimer = null;
            }
            localRemainingSecs = 0;
            pinInput.disabled = false;
            pinSubmit.disabled = false;
            pinSubmit.style.opacity = '1';
            pinSubmit.style.cursor = 'pointer';
        }

        function startLockoutCountdown(remainingMs) {
            localRemainingSecs = Math.ceil(remainingMs / 1000);
            updateLockoutUi();
            if (lockoutTimer) {
                clearInterval(lockoutTimer);
            }
            lockoutTimer = setInterval(() => {
                localRemainingSecs--;
                updateLockoutUi();
            }, 1000);
        }

        pinSubmit.addEventListener('click', performAuth);
        pinInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') performAuth();
        });

        async function performAuth() {
            if (localRemainingSecs > 0) return; // Prevent any action while locked out

            const enteredPin = pinInput.value.trim();
            if (!enteredPin) return;

            const isValid = /^[0-9]{4,8}$/.test(enteredPin);
            if (!isValid) {
                errorMsg.innerText = "PIN must contain 4–8 digits.";
                errorMsg.style.display = 'block';
                return;
            }

            errorMsg.style.display = 'none';
            try {
                const response = await fetch('/api/status', {
                    headers: { 'x-lan-password': enteredPin }
                });
                if (response.ok) {
                    pin = enteredPin;
                    localStorage.setItem('lan_sharing_pin', pin);
                    passwordOverlay.style.display = 'none';
                    isAuthRequired = false;
                    clearLockoutUi();
                    checkServerState();
                } else {
                    let errMsg = "Invalid PIN";
                    try {
                        const errData = await response.json();
                        if (errData) {
                            if (errData.error) {
                                errMsg = errData.error;
                            }
                            if (errData.locked && errData.remainingMs) {
                                startLockoutCountdown(errData.remainingMs);
                                return;
                            }
                        }
                    } catch (jsonErr) {}
                    errorMsg.innerText = errMsg;
                    errorMsg.style.display = 'block';
                }
            } catch (e) {
                errorMsg.innerText = "Invalid PIN";
                errorMsg.style.display = 'block';
                console.error("Auth request failed", e);
            }
        }

        function formatBytes(bytes, decimals = 2) {
            if (bytes === 0) return '0 Bytes';
            const k = 1024;
            const dm = decimals < 0 ? 0 : decimals;
            const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
        }
    </script>
</body>
</html>
"""
}
