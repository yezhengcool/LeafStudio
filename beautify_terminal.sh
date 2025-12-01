#!/bin/bash

# macOS 终端美化一键安装脚本
# 作者：LeafStudio
# 日期：2025-12-01

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🎨 macOS 终端美化工具"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${NC}"
echo ""

# 检查是否为 macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo -e "${RED}❌ 此脚本仅支持 macOS${NC}"
    exit 1
fi

# 函数：打印步骤
print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 1. 检查并安装 Homebrew
print_step "检查 Homebrew..."
if ! command -v brew &> /dev/null; then
    print_warning "未检测到 Homebrew，正在安装..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    # 检查是否为 Apple Silicon Mac
    if [[ $(uname -m) == "arm64" ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
    
    print_success "Homebrew 安装完成"
else
    print_success "Homebrew 已安装"
fi

# 2. 安装 Oh My Zsh
print_step "安装 Oh My Zsh..."
if [ ! -d "$HOME/.oh-my-zsh" ]; then
    sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended
    print_success "Oh My Zsh 安装完成"
else
    print_warning "Oh My Zsh 已安装，跳过"
fi

# 3. 安装 Powerlevel10k 主题
print_step "安装 Powerlevel10k 主题..."
P10K_DIR="${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}/themes/powerlevel10k"
if [ ! -d "$P10K_DIR" ]; then
    git clone --depth=1 https://github.com/romkatv/powerlevel10k.git "$P10K_DIR"
    print_success "Powerlevel10k 安装完成"
else
    print_warning "Powerlevel10k 已安装，跳过"
fi

# 4. 安装 Nerd Fonts
print_step "安装 Nerd Fonts..."
brew tap homebrew/cask-fonts 2>/dev/null || true

fonts=(
    "font-meslo-lg-nerd-font"
    "font-jetbrains-mono-nerd-font"
    "font-fira-code-nerd-font"
    "font-hack-nerd-font"
)

for font in "${fonts[@]}"; do
    if ! brew list --cask "$font" &> /dev/null; then
        print_step "  安装 $font..."
        brew install --cask "$font"
    else
        print_warning "  $font 已安装"
    fi
done
print_success "字体安装完成"

# 5. 安装 Zsh 插件
print_step "安装 Zsh 插件..."

# zsh-autosuggestions
AUTOSUGGESTIONS_DIR="${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-autosuggestions"
if [ ! -d "$AUTOSUGGESTIONS_DIR" ]; then
    git clone https://github.com/zsh-users/zsh-autosuggestions "$AUTOSUGGESTIONS_DIR"
    print_success "  zsh-autosuggestions 安装完成"
else
    print_warning "  zsh-autosuggestions 已安装"
fi

# zsh-syntax-highlighting
HIGHLIGHTING_DIR="${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-syntax-highlighting"
if [ ! -d "$HIGHLIGHTING_DIR" ]; then
    git clone https://github.com/zsh-users/zsh-syntax-highlighting.git "$HIGHLIGHTING_DIR"
    print_success "  zsh-syntax-highlighting 安装完成"
else
    print_warning "  zsh-syntax-highlighting 已安装"
fi

# 6. 安装实用 CLI 工具
print_step "安装实用 CLI 工具..."

tools=(
    "bat"           # 更好的 cat
    "exa"           # 更好的 ls
    "fzf"           # 模糊搜索
    "htop"          # 系统监控
    "tldr"          # 简化的帮助文档
    "ripgrep"       # 快速搜索
    "fd"            # 更好的 find
    "ncdu"          # 磁盘分析
    "neofetch"      # 系统信息
    "thefuck"       # 命令纠错
)

for tool in "${tools[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        print_step "  安装 $tool..."
        brew install "$tool"
    else
        print_warning "  $tool 已安装"
    fi
done
print_success "CLI 工具安装完成"

# 7. 备份现有配置
print_step "备份现有配置..."
if [ -f "$HOME/.zshrc" ]; then
    cp "$HOME/.zshrc" "$HOME/.zshrc.backup.$(date +%Y%m%d_%H%M%S)"
    print_success "配置文件已备份"
fi

# 8. 配置 .zshrc
print_step "配置 .zshrc..."

# 更新主题
if grep -q 'ZSH_THEME="robbyrussell"' "$HOME/.zshrc"; then
    sed -i '' 's/ZSH_THEME="robbyrussell"/ZSH_THEME="powerlevel10k\/powerlevel10k"/' "$HOME/.zshrc"
    print_success "  主题已更新为 Powerlevel10k"
fi

# 更新插件列表
if grep -q 'plugins=(git)' "$HOME/.zshrc"; then
    sed -i '' 's/plugins=(git)/plugins=(git zsh-autosuggestions zsh-syntax-highlighting colored-man-pages extract z docker)/' "$HOME/.zshrc"
    print_success "  插件列表已更新"
fi

# 添加自定义配置（如果还没有）
if ! grep -q "# LeafStudio Custom Aliases" "$HOME/.zshrc"; then
    cat >> "$HOME/.zshrc" << 'EOL'

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# LeafStudio Custom Aliases
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# 通用别名
alias ll='exa -lah --icons'
alias ls='exa --icons'
alias lt='exa --tree --level=2 --icons'
alias cat='bat'
alias c='clear'
alias cls='clear'

# 快速导航
alias ..='cd ..'
alias ...='cd ../..'
alias ....='cd ../../..'

# Git 快捷命令
alias gs='git status'
alias ga='git add'
alias gc='git commit -m'
alias gp='git push'
alias gl='git pull'
alias gco='git checkout'
alias gb='git branch'
alias glog='git log --oneline --graph --decorate --all'

# 开发工具
alias python='python3'
alias pip='pip3'

# 配置文件编辑
alias zshconfig='nano ~/.zshrc'
alias zshreload='source ~/.zshrc'

# 系统工具
alias myip='curl ifconfig.me'
alias path='echo $PATH | tr ":" "\n"'
alias ports='lsof -i -P -n | grep LISTEN'

# thefuck 集成
eval $(thefuck --alias)

# fzf 快捷键绑定
[ -f ~/.fzf.zsh ] && source ~/.fzf.zsh

# 启动欢迎信息
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Welcome back, $USER!"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

EOL
    print_success "  自定义配置已添加"
fi

# 9. 安装 iTerm2（可选）
print_step "检查 iTerm2..."
if ! brew list --cask iterm2 &> /dev/null; then
    read -p "是否安装 iTerm2（推荐，更强大的终端）？[y/N] " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        brew install --cask iterm2
        print_success "iTerm2 安装完成"
    fi
else
    print_success "iTerm2 已安装"
fi

# 10. 完成
echo ""
echo -e "${BLUE}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ 终端美化安装完成！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${NC}"
echo ""
echo -e "${GREEN}下一步操作：${NC}"
echo ""
echo "1. ${YELLOW}关闭并重新打开终端${NC}"
echo ""
echo "2. ${YELLOW}首次打开会自动启动 Powerlevel10k 配置向导${NC}"
echo "   如果没有自动启动，运行："
echo "   ${BLUE}p10k configure${NC}"
echo ""
echo "3. ${YELLOW}在终端偏好设置中更改字体${NC}"
echo "   - Terminal.app: Preferences → Profiles → Font"
echo "   - iTerm2: Preferences → Profiles → Text → Font"
echo "   推荐字体：${BLUE}MesloLGS NF, 13pt${NC}"
echo ""
echo "4. ${YELLOW}设置透明度（可选）${NC}"
echo "   - Terminal.app: Profiles → Window → Background → Opacity (90%)"
echo "   - iTerm2: Profiles → Window → Transparency (10-15)"
echo ""
echo "5. ${YELLOW}重新加载配置${NC}"
echo "   ${BLUE}source ~/.zshrc${NC}"
echo ""
echo -e "${GREEN}配置文件备份位置：${NC}"
echo "  ~/.zshrc.backup.*"
echo ""
echo -e "${GREEN}如需恢复旧配置：${NC}"
echo "  ${BLUE}cp ~/.zshrc.backup.* ~/.zshrc${NC}"
echo ""
echo -e "${BLUE}享受你的新终端！🎉${NC}"
echo ""
