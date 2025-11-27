# LeafStudio 激活码生成工具

## 使用说明

### 1. 获取机器码
用户在激活对话框中可以看到自己的机器码,格式如: `A1B2C3D4E5F6G7H8`

### 2. 生成激活码

使用以下 Kotlin 代码生成激活码:

```kotlin
import com.leafstudio.tvplayer.utils.ActivationManager

fun main() {
    // 示例1: 生成30天的激活码
    val machineCode = "A1B2C3D4E5F6G7H8"  // 用户的机器码
    val activationCode30Days = ActivationManager.generateActivationCodeByDays(machineCode, 30)
    println("30天激活码: $activationCode30Days")
    
    // 示例2: 生成365天(1年)的激活码
    val activationCode1Year = ActivationManager.generateActivationCodeByDays(machineCode, 365)
    println("1年激活码: $activationCode1Year")
    
    // 示例3: 生成指定日期的激活码
    val expiryDate = "2025-12-31 23:59:59"
    val activationCodeCustom = ActivationManager.generateActivationCode(machineCode, expiryDate)
    println("自定义日期激活码: $activationCodeCustom")
}
```

### 3. 激活码格式
激活码格式: `XXXX-XXXX-XXXX-XXXX` (自动格式化)

### 4. 测试示例

#### 机器码: `A1B2C3D4E5F6G7H8`
- 30天激活码: (运行代码生成)
- 1年激活码: (运行代码生成)

## 激活流程

1. 用户打开应用
2. 如果未激活,自动弹出激活对话框(无法关闭)
3. 用户复制机器码发送给管理员
4. 管理员使用上述代码生成激活码
5. 用户输入激活码并点击"激活"
6. 系统验证激活码:
   - 检查机器码是否匹配
   - 检查是否过期
   - 验证通过后保存激活信息
7. 激活成功,应用正常使用

## 激活状态管理

### 查看激活状态
- 菜单 -> 激活
- 显示过期时间和实时倒计时

### 倒计时格式
- `30天12小时30分钟15秒`
- 实时更新,精确到秒

### 过期处理
- 激活过期后,下次打开应用会强制要求重新激活
- 无法进行任何操作,直到重新激活

## 安全特性

1. **机器码绑定**: 激活码与设备唯一绑定,无法在其他设备使用
2. **AES加密**: 使用AES加密算法生成激活码
3. **时间验证**: 严格验证过期时间,无法篡改
4. **本地存储**: 激活信息加密存储在本地

## 注意事项

1. 每个机器码对应唯一的激活码
2. 激活码包含过期时间信息
3. 激活码格式固定,便于识别
4. 建议定期更新激活码以保证安全性
