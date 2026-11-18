# 暖灯与失语之森

`editable/` 是第一章内容包的可编辑权威源，`warm-lantern-1.0.0.fmbook` 是可由
账号书架直接安装的成品包。对白中的说话者 `旅伴` 由游戏映射为当前带队伙伴，
避免莉娅/瑟琳切换后出现立绘与说话者不一致。

在 `editable/` 目录重建成品包：

```sh
zip -q -X -r ../warm-lantern-1.0.0.fmbook .
```

成品包按 Git LFS 管理；修改 JSON/TSV 后先重新打包，再通过账号页预览和安装验证。
