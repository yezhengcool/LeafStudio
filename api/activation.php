<?php
/**
 * 激活API - PHP版本
 * 可以部署到任何支持PHP和PostgreSQL的服务器
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

// 处理OPTIONS请求
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// 只允许POST请求
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit();
}

// 数据库配置
define('DB_HOST', 'ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech');
define('DB_NAME', 'neondb');
define('DB_USER', 'neondb_owner');
define('DB_PASS', 'npg_kf5BO3mHDoTZ');
define('DB_PORT', '5432');

try {
    // 连接数据库
    $dsn = sprintf(
        "pgsql:host=%s;port=%s;dbname=%s;sslmode=require",
        DB_HOST,
        DB_PORT,
        DB_NAME
    );
    
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);

    // 解析请求
    $input = json_decode(file_get_contents('php://input'), true);
    $action = $input['action'] ?? null;
    $machineCode = $input['machineCode'] ?? null;

    if (!$action || !$machineCode) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing required parameters']);
        exit();
    }

    // 检查激活状态
    if ($action === 'check') {
        // 获取服务器时间（毫秒）
        $serverTime = intval(microtime(true) * 1000);

        // 查询设备记录
        $stmt = $pdo->prepare("SELECT expiry_time FROM activation_records WHERE machine_code = :machine_code");
        $stmt->execute(['machine_code' => $machineCode]);
        $device = $stmt->fetch();

        if (!$device) {
            // 设备不存在，自动注册（3天试用）
            $trialDays = 3;
            $expiryTime = $serverTime + ($trialDays * 24 * 60 * 60 * 1000);

            $stmt = $pdo->prepare("
                INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time, device_note)
                VALUES (:machine_code, 'TRIAL-AUTO', :activation_time, :expiry_time, '自动注册试用')
            ");
            $stmt->execute([
                'machine_code' => $machineCode,
                'activation_time' => $serverTime,
                'expiry_time' => $expiryTime
            ]);

            echo json_encode([
                'success' => true,
                'isValid' => true,
                'remainingSeconds' => intval(($expiryTime - $serverTime) / 1000),
                'expiryTime' => $expiryTime,
                'message' => "试用期剩余 {$trialDays} 天"
            ]);
            exit();
        }

        // 设备已存在
        $expiryTime = intval($device['expiry_time']);
        $remainingMillis = $expiryTime - $serverTime;
        $isValid = $remainingMillis > 0;

        echo json_encode([
            'success' => true,
            'isValid' => $isValid,
            'remainingSeconds' => $isValid ? intval($remainingMillis / 1000) : 0,
            'expiryTime' => $expiryTime,
            'message' => $isValid ? '已激活' : '激活已过期'
        ]);
        exit();
    }

    // 激活码激活
    if ($action === 'activate') {
        $activationCode = $input['activationCode'] ?? null;
        
        if (!$activationCode) {
            http_response_code(400);
            echo json_encode(['error' => 'Missing activation code']);
            exit();
        }

        // 验证激活码
        $stmt = $pdo->prepare("SELECT is_used, duration_days FROM activation_codes WHERE code = :code");
        $stmt->execute(['code' => $activationCode]);
        $codeInfo = $stmt->fetch();

        if (!$codeInfo) {
            echo json_encode([
                'success' => false,
                'message' => '激活码无效'
            ]);
            exit();
        }

        if ($codeInfo['is_used']) {
            echo json_encode([
                'success' => false,
                'message' => '激活码已被使用'
            ]);
            exit();
        }

        // 获取服务器时间
        $serverTime = intval(microtime(true) * 1000);
        $durationDays = intval($codeInfo['duration_days']);
        $durationMillis = $durationDays * 24 * 60 * 60 * 1000;

        // 查询设备是否存在
        $stmt = $pdo->prepare("SELECT expiry_time FROM activation_records WHERE machine_code = :machine_code");
        $stmt->execute(['machine_code' => $machineCode]);
        $device = $stmt->fetch();

        if ($device) {
            // 续费
            $currentExpiry = intval($device['expiry_time']);
            $newExpiryTime = ($currentExpiry > $serverTime ? $currentExpiry : $serverTime) + $durationMillis;

            $stmt = $pdo->prepare("
                UPDATE activation_records 
                SET expiry_time = :expiry_time, activation_code = :activation_code, updated_at = NOW()
                WHERE machine_code = :machine_code
            ");
            $stmt->execute([
                'expiry_time' => $newExpiryTime,
                'activation_code' => $activationCode,
                'machine_code' => $machineCode
            ]);
        } else {
            // 新激活
            $newExpiryTime = $serverTime + $durationMillis;
            
            $stmt = $pdo->prepare("
                INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time)
                VALUES (:machine_code, :activation_code, :activation_time, :expiry_time)
            ");
            $stmt->execute([
                'machine_code' => $machineCode,
                'activation_code' => $activationCode,
                'activation_time' => $serverTime,
                'expiry_time' => $newExpiryTime
            ]);
        }

        // 标记激活码已使用
        $stmt = $pdo->prepare("
            UPDATE activation_codes 
            SET is_used = true, used_by_machine = :machine_code, used_at = :used_at
            WHERE code = :code
        ");
        $stmt->execute([
            'machine_code' => $machineCode,
            'used_at' => $serverTime,
            'code' => $activationCode
        ]);

        echo json_encode([
            'success' => true,
            'message' => 'SUCCESS'
        ]);
        exit();
    }

    http_response_code(400);
    echo json_encode(['error' => 'Invalid action']);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'error' => 'Database error',
        'details' => $e->getMessage()
    ]);
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'error' => 'Internal server error',
        'details' => $e->getMessage()
    ]);
}
