# 素材与 Git LFS

角色立绘、背景、音效、音乐、视频和可分发故事包是产品资源；验收截图、录屏、生成草稿和日志是过程产物。两者按用途区分，不能只按文件扩展名删除。

## 存放与版本

- 已有成品资源继续放 `web/src/assets/`，按角色/场景/声音分组。source 保留必要的最终母版，runtime 放实际加载的压缩版本；失败候选和生成中间图放 `.local/assets-work/`。
- 新角色使用稳定 ID、服装 ID 和表情 ID，不能每次生成换一个随机文件名后让组件难以追踪。章节资源随内容清单维护版本、hash、格式和许可。
- 素材说明只保留来源、使用许可、身份/服装、必要制作提示和运行衍生关系；工具输出、重试轮次和截图流水账不提交。
- `.fmbook` 成品包使用 LFS；可编辑的 JSON/TSV/对白和素材清单使用普通 Git，以便审查变化。不要只提交一个不可编辑的 ZIP。
- SVG、JSON、YAML、Markdown 和源码默认普通 Git；不要因为“都是资源”把文本送进 LFS。

## LFS 规则

根 [.gitattributes](../../.gitattributes) 为常见位图、音频、视频、二进制美术母版与 `.fmbook` 声明 `filter=lfs diff=lfs merge=lfs -text`。匹配规则只有在 Git LFS 客户端和过滤器正确安装时才会真正生成指针。

在开始提交素材前确认：

```sh
git lfs version
git lfs install --local
git lfs track
git check-attr filter diff merge -- web/src/assets/companions/lia/runtime/neutral.png
```

客户端缺失时先用当前平台受信任的包管理器或 Git LFS 官方发行安装，再执行仓库级初始化。不能仅写 .gitattributes 就报告“所有历史资源已经迁移”。

## 已有成品素材的接入

以干净、已知归属的工作区为起点，先核对实际清单/大小与当前 `.gitattributes`，将当前版本匹配的成品文件重新暂存为 LFS 指针。现有资源目录可使用：

```sh
git add --renormalize -- web/src/assets
git lfs status
git diff --cached --stat
git lfs fsck
```

检查暂存 blob 确为有效 LFS 指针、工作区仍为原始可读图片/音频，抽查 OID 对应原文件哈希。以一个正常新提交完成当前版本转换，不运行会改写历史的 migrate/filter-repo。测试 fixture 的二进制若命中规则，也要明确检查，而非批量吞入无关文件。

旧提交里的大对象不会因为新提交改成 LFS 而从历史消失；彻底瘦身需要另行授权的历史迁移及协作者协调，不能擅自强推。

## 克隆 构建与交付

克隆者和构建环境须安装 LFS 并获取实际资源：

```sh
git lfs pull
git lfs fsck
```

构建前确认加载路径不是以 `version https://git-lfs.github.com/spec/v1` 开头的文本指针，否则应明确失败而非把损坏图片打包发布。CI checkout 需要启用 LFS 下载；Docker 构建上下文来自已经还原素材的工作树。不要把访问令牌写入 Dockerfile、代码或素材清单。

普通 push 需要安装后的 LFS hook 上传相关对象；只有用户授权推送时才检查远端存储能力与额度并执行，不为本地整理自动上传全部历史对象。可在隔离的本地 clone 中用本仓库的 LFS 对象验证指针还原，远端下载未实测要如实说明。

参考：[Git LFS 跟踪规则](https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-track.adoc)、[迁移行为](https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-migrate.adoc)。
