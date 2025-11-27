// 数据库配置
const DATABASE_URL = 'postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require';

console.log('Script.js: 脚本开始执行');

// 立即挂载全局函数（利用函数提升）
try {
    window.searchDevices = searchDevices;
    window.filterDevices = filterDevices;
    window.refreshDevices = refreshDevices;
    window.showExtendDialog = showExtendDialog;
    window.confirmExtend = confirmExtend;
    window.deleteDevice = deleteDevice;
    window.showDeviceDetail = showDeviceDetail;
    window.showEditNoteDialog = showEditNoteDialog;
    window.confirmEditNote = confirmEditNote;
    window.showGenerateCodesDialog = showGenerateCodesDialog;
    window.confirmGenerate = confirmGenerate;
    window.copyCode = copyCode;
    window.deleteCode = deleteCode;
    window.refreshCodes = refreshCodes;
    window.changePassword = changePassword;
    window.saveSettings = saveSettings;
    window.logout = logout;
    window.showDialog = showDialog;
    window.closeDialog = closeDialog;
    console.log('Script.js: 全局函数挂载成功');
} catch (e) {
    console.error('Script.js: 全局函数挂载失败', e);
}

// 数据库连接单例
let sqlInstance = null;
let isConnecting = false;

// 获取数据库连接（动态加载）
async function getSql() {
    if (sqlInstance) return sqlInstance;

    if (isConnecting) {
        // 简单的等待逻辑
        while (isConnecting) {
            await new Promise(r => setTimeout(r, 100));
            if (sqlInstance) return sqlInstance;
        }
    }

    isConnecting = true;
    console.log('Script.js: 开始动态加载数据库驱动...');

    try {
        // 动态导入，避免阻塞脚本执行
        const module = await import('https://cdn.jsdelivr.net/npm/@neondatabase/serverless@0.6.0/+esm');
        const neon = module.neon;

        sqlInstance = neon(DATABASE_URL);
        console.log('Script.js: 数据库驱动加载并连接成功');
        isConnecting = false;
        return sqlInstance;
    } catch (error) {
        console.error('Script.js: 数据库驱动加载失败', error);
        isConnecting = false;
        alert('无法连接到数据库服务器，请检查网络连接。\n错误: ' + error.message);
        throw error;
    }
}

// 全局数据
let devicesData = [];
let codesData = [];
let currentFilter = 'all';

// 检查登录状态
function checkLogin() {
    const isLoggedIn = localStorage.getItem('admin_logged_in');
    if (isLoggedIn !== 'true') {
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

// 页面初始化
document.addEventListener('DOMContentLoaded', async function () {
    console.log('Script.js: DOMContentLoaded');
    // 检查登录状态
    if (!checkLogin()) {
        return;
    }

    // 显示用户名
    const username = localStorage.getItem('admin_username') || 'LeafStudio';
    const userInfoElement = document.querySelector('.user-info span');
    if (userInfoElement) {
        userInfoElement.textContent = username;
    }

    initNavigation();

    // 预加载数据库连接
    getSql().catch(console.error);

    await loadDevices();
    await loadCodes();
    updateStats();

    // 每30秒自动刷新
    setInterval(async () => {
        if (document.querySelector('#devices-page').classList.contains('active')) {
            await loadDevices();
        }
    }, 30000);
});

// 导航切换
function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', async function (e) {
            e.preventDefault();
            const pageName = this.dataset.page;

            // 更新导航状态
            navItems.forEach(nav => nav.classList.remove('active'));
            this.classList.add('active');

            // 切换页面
            document.querySelectorAll('.page').forEach(page => {
                page.classList.remove('active');
            });
            document.getElementById(`${pageName}-page`).classList.add('active');

            // 更新标题
            const titles = {
                'devices': '设备管理',
                'codes': '激活码管理',
                'statistics': '统计分析',
                'settings': '系统设置'
            };
            document.getElementById('page-title').textContent = titles[pageName];

            // 加载对应数据
            if (pageName === 'devices') await loadDevices();
            if (pageName === 'codes') await loadCodes();
            if (pageName === 'statistics') await loadStatistics();
            if (pageName === 'settings') loadSettings();
        });
    });
}

// 加载设备列表
async function loadDevices() {
    try {
        const sql = await getSql(); // 获取连接
        const result = await sql`
            SELECT * FROM activation_records 
            ORDER BY created_at DESC
        `;

        const currentTime = Date.now();
        devicesData = result.map(record => {
            // 确保时间戳是数字类型
            const activationTime = Number(record.activation_time);
            const expiryTime = Number(record.expiry_time);

            return {
                machine_code: record.machine_code,
                activation_code: record.activation_code,
                activation_time: activationTime,
                expiry_time: expiryTime,
                device_note: record.device_note || '',
                remaining_days: Math.max(0, Math.floor((expiryTime - currentTime) / (24 * 60 * 60 * 1000))),
                created_at: new Date(record.created_at).getTime(),
                updated_at: new Date(record.updated_at).getTime()
            };
        });

        renderDevices(devicesData);
        updateStats();
    } catch (error) {
        console.error('加载设备失败:', error);
        // 不弹窗打扰用户，只在控制台显示
    }
}

// 渲染设备列表
function renderDevices(devices) {
    const tbody = document.getElementById('devices-table-body');

    if (devices.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8">
                    <div class="empty-state">
                        <div class="empty-state-icon">📱</div>
                        <div class="empty-state-text">暂无设备数据</div>
                        <div class="empty-state-subtext">等待设备激活</div>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = devices.map(device => {
        const status = getDeviceStatus(device.remaining_days);
        const activationDate = formatDate(device.activation_time);
        const expiryDate = formatDate(device.expiry_time);
        const deviceNote = device.device_note || '<span style="color: #666;">未设置</span>';

        return `
            <tr>
                <td><code>${device.machine_code}</code></td>
                <td>
                    <span class="device-note-text">${deviceNote}</span>
                    <button class="btn-small btn-secondary" onclick="showEditNoteDialog('${device.machine_code}', '${escapeHtml(device.device_note)}')">
                        ✏️
                    </button>
                </td>
                <td><code>${device.activation_code}</code></td>
                <td>${activationDate}</td>
                <td>${expiryDate}</td>
                <td>${device.remaining_days} 天</td>
                <td><span class="status-badge ${status.class}">${status.text}</span></td>
                <td>
                    <button class="btn-small btn-primary" onclick="showExtendDialog('${device.machine_code}', ${device.expiry_time})">
                        延长
                    </button>
                    <button class="btn-small btn-secondary" onclick="showDeviceDetail('${device.machine_code}')">
                        详情
                    </button>
                    <button class="btn-small btn-danger" onclick="deleteDevice('${device.machine_code}')">
                        删除
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

// 获取设备状态
function getDeviceStatus(remainingDays) {
    if (remainingDays < 0) {
        return { text: '已过期', class: 'status-expired' };
    } else if (remainingDays <= 7) {
        return { text: '即将过期', class: 'status-expiring' };
    } else {
        return { text: '已激活', class: 'status-active' };
    }
}

// 更新统计数据
function updateStats() {
    const total = devicesData.length;
    const active = devicesData.filter(d => d.remaining_days > 7).length;
    const expiring = devicesData.filter(d => d.remaining_days > 0 && d.remaining_days <= 7).length;
    const expired = devicesData.filter(d => d.remaining_days <= 0).length;

    document.getElementById('total-devices').textContent = total;
    document.getElementById('active-devices').textContent = active;
    document.getElementById('expiring-devices').textContent = expiring;
    document.getElementById('expired-devices').textContent = expired;

    // 更新激活码统计
    const totalCodes = codesData.length;
    const usedCodes = codesData.filter(c => c.is_used).length;
    const unusedCodes = totalCodes - usedCodes;

    document.getElementById('total-codes').textContent = totalCodes;
    document.getElementById('used-codes').textContent = usedCodes;
    document.getElementById('unused-codes').textContent = unusedCodes;
}

// 搜索设备
function searchDevices() {
    const keyword = document.getElementById('device-search').value.toLowerCase();
    const filtered = devicesData.filter(device =>
        device.machine_code.toLowerCase().includes(keyword) ||
        device.activation_code.toLowerCase().includes(keyword) ||
        (device.device_note && device.device_note.toLowerCase().includes(keyword))
    );
    renderDevices(filtered);
}

// 筛选设备
function filterDevices() {
    const filter = document.getElementById('device-filter').value;
    currentFilter = filter;

    let filtered = devicesData;
    if (filter === 'active') {
        filtered = devicesData.filter(d => d.remaining_days > 7);
    } else if (filter === 'expired') {
        filtered = devicesData.filter(d => d.remaining_days <= 0);
    } else if (filter === 'expiring') {
        filtered = devicesData.filter(d => d.remaining_days > 0 && d.remaining_days <= 7);
    }

    renderDevices(filtered);
}

// 刷新设备列表
async function refreshDevices() {
    await loadDevices();
}

// 显示延长对话框
function showExtendDialog(machineCode, currentExpiry) {
    document.getElementById('extend-machine-code').value = machineCode;
    document.getElementById('extend-current-expiry').value = formatDate(currentExpiry);

    // 计算新过期时间
    const daysInput = document.getElementById('extend-days');
    daysInput.addEventListener('input', function () {
        const days = parseInt(this.value) || 0;
        const newExpiry = currentExpiry + (days * 24 * 60 * 60 * 1000);
        document.getElementById('extend-new-expiry').value = formatDate(newExpiry);
    });

    // 初始计算
    const days = parseInt(daysInput.value) || 30;
    const newExpiry = currentExpiry + (days * 24 * 60 * 60 * 1000);
    document.getElementById('extend-new-expiry').value = formatDate(newExpiry);

    showDialog('extend-dialog');
}

// 确认延长
async function confirmExtend() {
    const machineCode = document.getElementById('extend-machine-code').value;
    const days = parseInt(document.getElementById('extend-days').value);

    try {
        // 查询当前记录
        const sql = await getSql();
        const result = await sql`
            SELECT expiry_time FROM activation_records 
            WHERE machine_code = ${machineCode}
        `;

        if (result.length === 0) {
            alert('未找到该设备');
            return;
        }

        const currentExpiry = Number(result[0].expiry_time);
        const newExpiry = currentExpiry + (days * 24 * 60 * 60 * 1000);

        // 更新过期时间
        await sql`
            UPDATE activation_records 
            SET expiry_time = ${newExpiry}, updated_at = NOW() 
            WHERE machine_code = ${machineCode}
        `;

        alert('延长成功！');
        closeDialog('extend-dialog');
        await loadDevices();
    } catch (error) {
        console.error('延长失败:', error);
        alert('延长失败：' + error.message);
    }
}

// 删除设备
async function deleteDevice(machineCode) {
    if (!confirm(`确定要删除设备 ${machineCode} 吗？`)) {
        return;
    }

    try {
        const sql = await getSql();
        await sql`
            DELETE FROM activation_records 
            WHERE machine_code = ${machineCode}
        `;

        alert('删除成功！');
        await loadDevices();
    } catch (error) {
        console.error('删除失败:', error);
        alert('删除失败：' + error.message);
    }
}

// 显示设备详情
function showDeviceDetail(machineCode) {
    const device = devicesData.find(d => d.machine_code === machineCode);
    if (!device) return;

    const content = `
        <div class="form-group">
            <label>机器码</label>
            <input type="text" value="${device.machine_code}" readonly>
        </div>
        <div class="form-group">
            <label>设备备注</label>
            <input type="text" value="${device.device_note || '未设置'}" readonly>
        </div>
        <div class="form-group">
            <label>激活码</label>
            <input type="text" value="${device.activation_code}" readonly>
        </div>
        <div class="form-group">
            <label>激活时间</label>
            <input type="text" value="${formatDate(device.activation_time)}" readonly>
        </div>
        <div class="form-group">
            <label>过期时间</label>
            <input type="text" value="${formatDate(device.expiry_time)}" readonly>
        </div>
        <div class="form-group">
            <label>剩余天数</label>
            <input type="text" value="${device.remaining_days} 天" readonly>
        </div>
        <div class="form-group">
            <label>状态</label>
            <input type="text" value="${getDeviceStatus(device.remaining_days).text}" readonly>
        </div>
    `;

    document.getElementById('device-detail-content').innerHTML = content;
    showDialog('device-detail-dialog');
}

// 显示编辑备注对话框
function showEditNoteDialog(machineCode, currentNote) {
    document.getElementById('edit-note-machine-code').value = machineCode;
    document.getElementById('edit-note-input').value = currentNote;
    showDialog('edit-note-dialog');
}

// 确认编辑备注
async function confirmEditNote() {
    const machineCode = document.getElementById('edit-note-machine-code').value;
    const note = document.getElementById('edit-note-input').value.trim();

    try {
        const sql = await getSql();
        await sql`
            UPDATE activation_records 
            SET device_note = ${note}, updated_at = NOW() 
            WHERE machine_code = ${machineCode}
        `;

        alert('备注更新成功！');
        closeDialog('edit-note-dialog');
        await loadDevices();
    } catch (error) {
        console.error('更新备注失败:', error);
        alert('更新备注失败：' + error.message);
    }
}

// 加载激活码列表
async function loadCodes() {
    try {
        const sql = await getSql();
        const result = await sql`
            SELECT * FROM activation_codes 
            ORDER BY created_at DESC
        `;

        codesData = result.map(record => ({
            code: record.code,
            duration_days: record.duration_days,
            is_used: record.is_used,
            used_by_machine: record.used_by_machine,
            used_at: record.used_at ? Number(record.used_at) : null,
            created_at: new Date(record.created_at).getTime()
        }));

        renderCodes(codesData);
        updateStats();
    } catch (error) {
        console.error('加载激活码失败:', error);
        alert('加载激活码列表失败：' + error.message);
    }
}

// 渲染激活码列表
function renderCodes(codes) {
    const tbody = document.getElementById('codes-table-body');

    if (codes.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7">
                    <div class="empty-state">
                        <div class="empty-state-icon">🎫</div>
                        <div class="empty-state-text">暂无激活码</div>
                        <div class="empty-state-subtext">点击"生成激活码"按钮创建</div>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = codes.map(code => {
        return `
            <tr>
                <td><code>${code.code}</code></td>
                <td>${code.duration_days} 天</td>
                <td><span class="status-badge ${code.is_used ? 'status-used' : 'status-unused'}">
                    ${code.is_used ? '已使用' : '未使用'}
                </span></td>
                <td>${code.used_by_machine ? `<code>${code.used_by_machine}</code>` : '-'}</td>
                <td>${code.used_at ? formatDate(code.used_at) : '-'}</td>
                <td>${formatDate(code.created_at)}</td>
                <td>
                    ${!code.is_used ? `
                        <button class="btn-small btn-secondary" onclick="copyCode('${code.code}')">
                            复制
                        </button>
                        <button class="btn-small btn-danger" onclick="deleteCode('${code.code}')">
                            删除
                        </button>
                    ` : '-'}
                </td>
            </tr>
        `;
    }).join('');
}

// 显示生成激活码对话框
function showGenerateCodesDialog() {
    showDialog('generate-dialog');
}

// 确认生成激活码
async function confirmGenerate() {
    const count = parseInt(document.getElementById('generate-count').value);
    const days = parseInt(document.getElementById('generate-days').value);

    if (count <= 0 || count > 1000) {
        alert('生成数量必须在 1-1000 之间');
        return;
    }

    try {
        const sql = await getSql();
        const generatedCodes = [];

        for (let i = 0; i < count; i++) {
            const code = generateActivationCode();

            await sql`
                INSERT INTO activation_codes 
                (code, duration_days, is_used, created_at)
                VALUES (${code}, ${days}, false, NOW())
            `;

            generatedCodes.push(code);
        }

        alert(`成功生成 ${count} 个激活码！`);
        closeDialog('generate-dialog');
        await loadCodes();
    } catch (error) {
        console.error('生成失败:', error);
        alert('生成失败：' + error.message);
    }
}

// 生成随机激活码
function generateActivationCode() {
    const chars = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    let code = '';
    for (let i = 0; i < 3; i++) {
        for (let j = 0; j < 4; j++) {
            code += chars[Math.floor(Math.random() * chars.length)];
        }
        if (i < 2) code += '-';
    }
    return code;
}

// 复制激活码
function copyCode(code) {
    navigator.clipboard.writeText(code).then(() => {
        alert('激活码已复制到剪贴板');
    }).catch(err => {
        console.error('复制失败:', err);
        alert('复制失败，请手动复制');
    });
}

// 删除激活码
async function deleteCode(code) {
    if (!confirm(`确定要删除激活码 ${code} 吗？`)) {
        return;
    }

    try {
        const sql = await getSql();
        await sql`
            DELETE FROM activation_codes 
            WHERE code = ${code}
        `;

        alert('删除成功！');
        await loadCodes();
    } catch (error) {
        console.error('删除失败:', error);
        alert('删除失败：' + error.message);
    }
}

// 刷新激活码列表
async function refreshCodes() {
    await loadCodes();
}

// 加载统计数据
async function loadStatistics() {
    console.log('加载统计数据');
}

// 加载设置页面
function loadSettings() {
    const username = localStorage.getItem('admin_username') || 'LeafStudio';
    document.getElementById('current-username').value = username;

    const loginTime = localStorage.getItem('login_time');
    if (loginTime) {
        const date = new Date(loginTime);
        document.getElementById('login-time').value = formatDate(date.getTime());
    } else {
        document.getElementById('login-time').value = '未知';
    }
}

// 修改密码
async function changePassword() {
    const currentPassword = document.getElementById('current-password').value;
    const newPassword = document.getElementById('new-password').value;
    const confirmPassword = document.getElementById('confirm-password').value;
    const username = localStorage.getItem('admin_username') || 'LeafStudio';

    if (!currentPassword || !newPassword || !confirmPassword) {
        alert('请填写所有密码字段');
        return;
    }

    if (newPassword.length < 6) {
        alert('新密码至少需要6位字符');
        return;
    }

    if (newPassword !== confirmPassword) {
        alert('两次输入的新密码不一致');
        return;
    }

    try {
        // 查询用户
        const sql = await getSql();
        const result = await sql`
            SELECT * FROM admin_users 
            WHERE username = ${username}
        `;

        if (result.length === 0) {
            alert('用户不存在');
            return;
        }

        const admin = result[0];

        // 验证当前密码
        const currentHash = md5(currentPassword);
        if (currentHash !== admin.password_hash) {
            alert('当前密码错误');
            return;
        }

        // 更新密码
        const newHash = md5(newPassword);
        await sql`
            UPDATE admin_users 
            SET password_hash = ${newHash}, updated_at = NOW() 
            WHERE id = ${admin.id}
        `;

        alert('密码修改成功！下次登录时请使用新密码');

        document.getElementById('current-password').value = '';
        document.getElementById('new-password').value = '';
        document.getElementById('confirm-password').value = '';
    } catch (error) {
        console.error('修改密码失败:', error);
        alert('修改密码失败：' + error.message);
    }
}

// 保存设置
function saveSettings() {
    const apiUrl = document.getElementById('api-url').value;
    // 这里暂时只保存到 localStorage，实际应用中可能不需要保存 API URL，因为它是硬编码的
    // 或者可以将其保存起来用于覆盖默认配置
    alert('设置已保存（当前仅演示）');
}

// 退出登录
function logout() {
    if (confirm('确定要退出登录吗？')) {
        localStorage.removeItem('admin_logged_in');
        localStorage.removeItem('login_time');
        window.location.href = 'login.html';
    }
}

// 对话框控制
function showDialog(dialogId) {
    document.getElementById(dialogId).classList.add('active');
}

function closeDialog(dialogId) {
    document.getElementById(dialogId).classList.remove('active');
}

// 格式化日期
function formatDate(timestamp) {
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

// HTML 转义函数
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}
