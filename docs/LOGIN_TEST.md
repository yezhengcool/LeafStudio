# 登录功能测试指南

## 🧪 测试清单

### 测试 1：未登录访问保护

**测试步骤：**
1. 清除浏览器 localStorage（或使用无痕模式）
2. 直接访问 `https://yezheng.dpdns.org/tv/index.html`

**预期结果：**
- ✅ 立即跳转到 `login.html`
- ✅ 不会看到管理后台的任何内容
- ✅ 没有页面闪烁

---

### 测试 2：登录功能

**测试步骤：**
1. 访问 `https://yezheng.dpdns.org/tv/login.html`
2. 输入用户名：`Leaf Studio`
3. 输入密码：`Test123456`
4. 点击"登录"按钮

**预期结果：**
- ✅ 显示加载动画
- ✅ 跳转到 `index.html`
- ✅ 显示管理后台界面
- ✅ 右上角显示用户名 "Leaf Studio"

---

### 测试 3：错误密码

**测试步骤：**
1. 访问登录页面
2. 输入用户名：`Leaf Studio`
3. 输入错误密码：`wrong123`
4. 点击"登录"

**预期结果：**
- ✅ 显示错误提示："用户名或密码错误"
- ✅ 错误提示有震动动画
- ✅ 密码输入框被清空
- ✅ 焦点回到密码输入框

---

### 测试 4：登录状态保持

**测试步骤：**
1. 成功登录后
2. 刷新页面（F5）
3. 或关闭浏览器重新打开

**预期结果：**
- ✅ 仍然保持登录状态
- ✅ 直接显示管理后台
- ✅ 不需要重新登录

---

### 测试 5：修改密码

**测试步骤：**
1. 登录后点击左侧菜单"系统设置"
2. 在"修改密码"部分：
   - 当前密码：`Test123456`
   - 新密码：`NewPass123`
   - 确认密码：`NewPass123`
3. 点击"修改密码"

**预期结果：**
- ✅ 显示成功提示
- ✅ 输入框被清空
- ✅ 退出登录后使用新密码可以登录

---

### 测试 6：密码修改验证

**测试步骤：**
1. 在修改密码时输入错误的当前密码
2. 或新密码少于6位
3. 或两次新密码不一致

**预期结果：**
- ✅ 显示相应的错误提示
- ✅ 密码不会被修改

---

### 测试 7：退出登录

**测试步骤：**
1. 点击右上角"退出"按钮
2. 确认退出

**预期结果：**
- ✅ 跳转到登录页面
- ✅ 再次访问 `index.html` 会跳转到登录页

---

### 测试 8：密码显示/隐藏

**测试步骤：**
1. 在登录页面输入密码
2. 点击右侧的眼睛图标 👁️

**预期结果：**
- ✅ 密码从隐藏变为可见
- ✅ 图标从 👁️ 变为 🙈
- ✅ 再次点击恢复隐藏

---

### 测试 9：多浏览器测试

**测试步骤：**
1. 在 Chrome 登录
2. 在 Firefox 访问管理后台

**预期结果：**
- ✅ Firefox 需要重新登录
- ✅ 两个浏览器的登录状态独立

---

### 测试 10：清除登录状态

**测试步骤：**
1. 登录后
2. 打开浏览器开发者工具（F12）
3. Application → Local Storage
4. 删除 `admin_logged_in`

**预期结果：**
- ✅ 刷新页面后跳转到登录页

---

## 🔧 调试方法

### 查看登录状态

打开浏览器开发者工具（F12），在 Console 中执行：

```javascript
// 查看所有登录相关信息
console.log('登录状态:', localStorage.getItem('admin_logged_in'));
console.log('用户名:', localStorage.getItem('admin_username'));
console.log('登录时间:', localStorage.getItem('login_time'));
```

### 手动设置登录状态

```javascript
// 手动登录
localStorage.setItem('admin_logged_in', 'true');
localStorage.setItem('admin_username', 'Leaf Studio');
localStorage.setItem('login_time', new Date().toISOString());
window.location.reload();
```

### 重置密码

```javascript
// 重置为默认密码
localStorage.setItem('admin_password', 'Test123456');
alert('密码已重置为: Test123456');
```

### 清除所有登录信息

```javascript
// 完全清除
localStorage.removeItem('admin_logged_in');
localStorage.removeItem('admin_username');
localStorage.removeItem('admin_password');
localStorage.removeItem('login_time');
window.location.reload();
```

---

## ✅ 验收标准

所有测试通过后，系统应该：

- ✅ 未登录无法访问管理后台
- ✅ 登录功能正常
- ✅ 密码修改功能正常
- ✅ 退出登录功能正常
- ✅ 登录状态保持正常
- ✅ 错误提示清晰明确
- ✅ 界面美观流畅

---

## 🐛 常见问题排查

### 问题 1：直接访问 index.html 没有跳转

**可能原因：**
- 浏览器缓存了旧版本的 index.html
- JavaScript 被禁用

**解决方法：**
1. 强制刷新（Ctrl+F5 或 Cmd+Shift+R）
2. 清除浏览器缓存
3. 检查浏览器是否启用 JavaScript

### 问题 2：登录后显示空白页

**可能原因：**
- CSS 文件未加载
- JavaScript 错误

**解决方法：**
1. 打开开发者工具查看 Console 错误
2. 检查 Network 标签，确认所有文件已加载
3. 确认 style.css 和 script.js 在同一目录

### 问题 3：修改密码后无法登录

**可能原因：**
- 新密码记错了

**解决方法：**
1. 使用开发者工具重置密码（见上方"重置密码"）
2. 使用默认密码 `Test123456` 登录

---

## 📝 测试报告模板

```
测试日期：2025-11-26
测试人员：
浏览器版本：

测试结果：
[ ] 测试 1：未登录访问保护 - 通过/失败
[ ] 测试 2：登录功能 - 通过/失败
[ ] 测试 3：错误密码 - 通过/失败
[ ] 测试 4：登录状态保持 - 通过/失败
[ ] 测试 5：修改密码 - 通过/失败
[ ] 测试 6：密码修改验证 - 通过/失败
[ ] 测试 7：退出登录 - 通过/失败
[ ] 测试 8：密码显示/隐藏 - 通过/失败
[ ] 测试 9：多浏览器测试 - 通过/失败
[ ] 测试 10：清除登录状态 - 通过/失败

问题记录：
（如有问题请详细描述）

总体评价：
（通过/需要修复）
```

---

## 🎯 快速验证

最快的验证方法：

1. **清除 localStorage**
   ```javascript
   localStorage.clear();
   ```

2. **访问管理后台**
   ```
   https://yezheng.dpdns.org/tv/index.html
   ```

3. **应该立即跳转到登录页**
   ```
   https://yezheng.dpdns.org/tv/login.html
   ```

4. **使用默认账户登录**
   - 用户名：`Leaf Studio`
   - 密码：`Test123456`

5. **成功进入管理后台**

如果以上流程顺利完成，说明登录功能正常！✅
