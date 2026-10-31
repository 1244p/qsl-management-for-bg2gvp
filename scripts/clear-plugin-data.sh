#!/usr/bin/env bash
#
# 清空 QSL 管理插件的数据（Linux / macOS / WSL 版，走插件自带的扩展 API 批量删除）。
#
# 不碰数据库、不需要停 Halo：逐条调 Halo 的扩展 CRUD 接口，索引由 Halo 自己维护。
# 凭据用 Halo 个人令牌：控制台 → 个人中心 → 个人令牌 → 新建，形如 pat_xxx。
#
# 用法：
#   TOKEN=pat_xxx ./scripts/clear-plugin-data.sh --dry-run          # 只看会删多少
#   TOKEN=pat_xxx ./scripts/clear-plugin-data.sh                    # 只清业务数据
#   TOKEN=pat_xxx ./scripts/clear-plugin-data.sh --scope all        # 连配置一起清（恢复出厂）
#
# 环境变量：
#   BASE_URL  站点地址，默认 http://127.0.0.1:8090
#   TOKEN     Halo 个人令牌（必填）
#   PYTHON    解析 JSON 用的解释器，默认 python3
#
# Halo 跑在 Docker 里时，在宿主机跑本脚本即可（端口映射到 127.0.0.1:8090 就是默认值）。

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8090}"
BASE_URL="${BASE_URL%/}"
TOKEN="${TOKEN:-}"
PYTHON="${PYTHON:-python3}"
SCOPE="business"
DRY_RUN=0
API_ROOT="/apis/qsl-management.bi1kbu.com/v1alpha1"

usage() {
    sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        --scope)
            shift
            SCOPE="${1:-}"
            ;;
        --scope=*) SCOPE="${1#*=}" ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知参数：$1（用 --help 看用法）" >&2; exit 2 ;;
    esac
    shift
done

case "$SCOPE" in
    business|all) ;;
    *) echo "SCOPE 只能是 business 或 all，收到：$SCOPE" >&2; exit 2 ;;
esac

if [ -z "$TOKEN" ]; then
    echo "缺少 TOKEN 环境变量。个人令牌形如 pat_xxx，用 TOKEN=pat_xxx ./scripts/clear-plugin-data.sh 运行。" >&2
    exit 2
fi
case "$TOKEN" in
    pat_*) ;;
    *) echo "提示：个人令牌通常以 pat_ 开头，请确认没有把别的凭据贴进来。" >&2 ;;
esac

command -v curl >/dev/null 2>&1 || { echo "需要 curl" >&2; exit 2; }
command -v "$PYTHON" >/dev/null 2>&1 || { echo "需要 python3（用于解析 JSON，Ubuntu 默认就有）" >&2; exit 2; }

# 业务数据：清了就是从零开始录入
business_plurals=(
    qso-records
    card-records
    receive-records
    offline-exchange-cards
    exchange-requests
    qsl-card-requests
    qsl-card-request-qso-reservations
    import-export-jobs
    log-sync-jobs
    qsl-audit-logs
)

# 配置数据：清了重启插件会重建 system-settings 与 station-profiles 的默认值（呼号回落 BI1KBU）
config_plurals=(
    log-sync-profiles
    station-cards
    address-book-entries
    bureau-entries
    equipment-catalog-entries
    station-equipments
    offline-activities
    station-profiles
    system-settings
    qsl-migration-states
)

targets=("${business_plurals[@]}")
if [ "$SCOPE" = "all" ]; then
    targets+=("${config_plurals[@]}")
fi

get_total() {
    curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE_URL$API_ROOT/$1?page=0&size=1" \
        | "$PYTHON" -c 'import sys, json; print(json.load(sys.stdin).get("total", 0))' \
        | tr -d '\r'
}

list_names() {
    # 名字里混进回车会让 curl 报 URL 非法（Windows 侧的 python 输出是 CRLF），故统一 tr 掉
    # "URL rejected: Malformed input to a URL function"；Linux 上无副作用
    curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE_URL$API_ROOT/$1?page=0&size=100" \
        | "$PYTHON" -c 'import sys, json
for item in json.load(sys.stdin).get("items") or []:
    print(item["metadata"]["name"])' \
        | tr -d '\r'
}

delete_one() {
    local plural="$1" name="$2" code
    code=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE \
        -H "Authorization: Bearer $TOKEN" "$BASE_URL$API_ROOT/$plural/$name")
    case "$code" in
        200|202|204) return 0 ;;
        404) echo "    （已不存在，跳过）$plural/$name" >&2; return 0 ;;
        *) echo "    删除失败 HTTP $code：$plural/$name" >&2; return 1 ;;
    esac
}

clear_plural() {
    local plural="$1" removed=0 round=0 failed=0 names name
    while :; do
        round=$((round + 1))
        # 用命令替换而不是进程替换：某些 shell（git-bash、旧 dash）对 < <() 支持不稳，会静默死循环
        names=$(list_names "$plural")
        if [ -z "$names" ]; then
            break
        fi
        while IFS= read -r name; do
            [ -z "$name" ] && continue
            if delete_one "$plural" "$name"; then
                removed=$((removed + 1))
            else
                failed=$((failed + 1))
            fi
        done <<< "$names"
        if [ "$round" -gt 1000 ]; then
            echo "警告：$plural 删除轮次异常，已停止" >&2
            break
        fi
    done
    if [ "$failed" -gt 0 ]; then
        echo "  警告：$plural 有 $failed 条删除失败（权限不足或被引用）" >&2
    fi
    echo "$removed"
}

echo "站点：$BASE_URL    范围：$SCOPE    $([ "$DRY_RUN" -eq 1 ] && echo '（预览，不删除）')"
echo ""
printf '%-38s %8s %8s\n' "类型" "清理前" "已删除"
printf '%-38s %8s %8s\n' "--------------------------------------" "--------" "--------"

total_removed=0
for plural in "${targets[@]}"; do
    before=$(get_total "$plural")
    if [ "$before" -eq 0 ]; then
        printf '%-38s %8s %8s\n' "$plural" 0 0
        continue
    fi
    if [ "$DRY_RUN" -eq 1 ]; then
        printf '%-38s %8s %8s
' "$plural" "$before" "$before"
        total_removed=$((total_removed + before))
        continue
    fi
    echo "清理 $plural（$before 条）..."
    removed=$(clear_plural "$plural")
    printf '%-38s %8s %8s\n' "$plural" "$before" "$removed"
    total_removed=$((total_removed + removed))
done

echo ""
echo "合计处理 $total_removed 条。"

if [ "$DRY_RUN" -eq 1 ]; then
    echo "以上为预览，去掉 --dry-run 才会真正删除。"
    exit 0
fi

cat <<'TIP'

扩展数据已处理完毕。还需要手工清两处：
  1) 卡片图片：控制台 → 附件，删除卡片版本/制卡时上传的图（删扩展实例不会删附件）。
  2) 浏览器 localStorage：控制台 F12 执行
     Object.keys(localStorage).filter(k => k.startsWith('qsl:')).forEach(k => localStorage.removeItem(k))
TIP

if [ "$SCOPE" = "all" ]; then
    echo "  3) 重启插件（或重启 Halo）后，系统参数与本台资料会重建为默认值，呼号回落 BI1KBU。"
fi
