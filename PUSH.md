# 推上 GitHub（私有仓库）

1. 解压本 zip（里面有完整 git 仓库，直接 git push 就行）
2. 在 GitHub 网页上 New repository → 名字 `fallguard`（或随意）→ **Private** → 创建（不要勾 README）
3. 在解压目录里执行：

```
cd fallguard
git remote add origin git@github.com:你的用户名/fallguard.git   # 或用 https 地址
git push -u origin main
```

用 https 的话 remote 换成
`https://github.com/你的用户名/fallguard.git`，push 时输入用户名 + PAT。

注意：dist/keystore.jks 是签名密钥（密码 fall123），
私有仓库里放着没问题，别改成公开。
