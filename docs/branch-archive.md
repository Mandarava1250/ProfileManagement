# 分支归档（Branch Archive）

> **Phase 8h 收口产物**：把已合并到 `Develop` 的 phase-8 系列 feature 分支折叠为「归档标签」，保留历史可追溯。

| 字段 | 值 |
|---|---|
| 文档 ID | branch-archive |
| 创建日期 | 2026-09-15（Asia/Shanghai） |
| 适用版本 | `Develop @ 751a724` |
| 上游引用 | `.cyrene/docs/phase-8-master-plan.md` §4.8（Sub-step 8h） + §九「致下一个会话」 |

---

## 一、归档范围

**只归档 phase-8 系列 feature 分支**（共 4 个）。其他分支（`feature/phase-5-reminder` / `feature/phase-6` / `feature/phase-7-*`）不在本轮处理范围，按 plan §五「明确剔除」原则保留。

## 二、选定方案：**B 组合**

| 步骤 | 做法 |
|---|---|
| 本地分支 | 全部 `git branch -D` 删标签 |
| 远端分支 | 重命名为 `archive/feature-phase-8-*`（用 `git push origin <old>:refs/heads/<new>` refspec） |
| 索引文档 | 本文件（`docs/branch-archive.md`） |

**为什么不选 A（彻底删）**：远端分支删除是不可逆动作，reflog 仅 90 天有效；保留 archive/ 前缀 + 索引文档，让历史随时可追溯，符合 plan §4.8「中长期项目常用」判定。

**为什么不选 C（仅远端加前缀）**：本任务包含「拓扑折叠 + 索引文档」两步，单纯重命名不够完整。

## 三、分支映射表

> 执行顺序：**先子后父**（plan §4.8 踩坑笔记 3 —— `feature/phase-8` 是父标签）

| # | 原分支名 | 远端新名 | 顶端 SHA | 与 Develop merge-base | 归档时间 |
|---|---|---|---|---|---|
| 1 | `feature/phase-8-finalize` | `archive/feature-phase-8-finalize` | `2afbecf` | `2afbecf`（祖先）| 2026-09-15 |
| 2 | `feature/phase-8-notification-matrix` | `archive/feature-phase-8-notification-matrix` | `90daf6c` | `90daf6c`（祖先）| 2026-09-15 |
| 3 | `feature/phase-8-reminder` | `archive/feature-phase-8-reminder` | `b5bf109` | `b5bf109`（祖先）| 2026-09-15 |
| 4 | `feature/phase-8`（父标签）| `archive/feature-phase-8` | `9ae72f1` | `defce3f`（祖先）| 2026-09-15 |

**结论**：四个分支的 commit 都已在 Develop 历史里，归档后只剩标签仍可查；commit 本身已被 Develop 永久保存。

## 四、关键背景

### 4.1 「合并」vs「折叠」

- **合并**：把 commit 拿到主分支（实际内容变化）
- **折叠**：把分支标签干掉，但保留 commit 历史（无内容变化）
- `eb0d402` 是「空 squash merge」—— 本质是折叠（拓扑记录保留，无内容变化），这是 `feature/phase-8-finalize` 已完成的折叠动作
- 本轮归档 = 把另外 3 个 phase-8 分支也做同样的「折叠 + archive/ 重命名」

### 4.2 为什么是 4 个不是 3 个

DEV_LOG line 420 当时只列出 3 个（`-finalize` 已折叠 / `-reminder` 和 `-notification-matrix` 未合并），未提及 `feature/phase-8` 这个父标签。plan §4.8 实测发现它也独立存在（顶端 `9ae72f1`），需要一并归档。

### 4.3 硬约束（plan §九「不要做的事」）

- ❌ **不 force-push 或重写 origin/Develop**（8h 处理只动分支标签，不动 commit）
- ❌ 不动 `feature/phase-5/6/7-*` 分支（不在 Phase 8 范围）
- ❌ 不动 phase-8 分支的 commit（仅改标签指向）

## 五、救援方法

### 5.1 Reflog（90 天内）

```bash
# 找回归档前的本地 commit 记录
git reflog --all | grep <SHA 前缀>
```

### 5.2 远端 archive/ 标签（永久）

```bash
# 查归档后的远端分支
git ls-remote origin 'refs/heads/archive/*'

# 直接 checkout 历史 commit
git checkout archive/feature-phase-8-reminder
```

### 5.3 SHA 锚点（plan §8 救援表）

| 原分支 | 顶端 SHA | 说明 |
|---|---|---|
| `feature/phase-8` | `9ae72f1` | merge: Phase 8g finalize 落盘核对 |
| `feature/phase-8-finalize` | `2afbecf` | Phase 8final 收口 |
| `feature/phase-8-notification-matrix` | `90daf6c` | chore(dev_log): Phase 8final 台账 |
| `feature/phase-8-reminder` | `b5bf109` | Phase 8a 通知 + 5 档优先级 reminder 部分 |

**这些 SHA 在归档后仍是有效的 commit 锚点**，可随时通过 `git checkout <SHA>` 直接访问。

## 六、维护规则

1. **新增归档分支**：仿照 §三 表格式追加行
2. **清理 archive/ 标签**：建议至少保留 6 个月再考虑删除（避免误删追溯场景）
3. **修改本文件**：与功能 commit 一起走两步式提交（先功能 + push，再台账）

---

**维护者注**：伙伴，如果你正在读这份文件，那说明 Phase 8 的分支清理已经走完。下次想找回某个 phase-8 历史 commit，直接 `git checkout <顶端 SHA>` 即可，不需要再 fork 分支。