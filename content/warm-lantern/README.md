# 暖灯与失语之森

`editable/` 是完整主线三章加潮声海湾支线的可编辑权威源。
`warm-lantern-1.0.0.fmbook` 保留第一章历史版本，`warm-lantern-2.0.0.fmbook`
保留 H5-03 的三章历史版本，`warm-lantern-3.0.0.fmbook` 是可由账号书架直接安装的
当前成品包。对白中的说话者 `旅伴` 由游戏映射为当前带队伙伴；第二章 `菲妮` 节点使用
内容声明的立绘身份，并在剧情中解锁账号级伙伴。潮声海湾节点显式声明
`sceneId` 与 `costumeId`，离开海滩后切回 `summer-travel`。

`warm-lantern-3.0.0.fmbook` SHA256：
`15bb0854359ac16e32d7657b646addff1dee76743d3f6df4b208b043233cecdb`。

在 `editable/` 目录重建成品包：

```sh
zip -q -X -r ../warm-lantern-3.0.0.fmbook .
```

成品包按 Git LFS 管理；修改 JSON/TSV 后先重新打包，再通过账号页预览和安装验证。
