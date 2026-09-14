# Constrained DEV Image Lock

Checked against Docker Hub tag metadata on 2026-07-11.

| Image | Multi-arch tag digest | Linux amd64 digest |
|---|---|---|
| `mysql:8.4.10` | `sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af` | `sha256:ef9038553b7ea407704f16770e407ffd32f5566f125d0d94f63ff736af1d43f8` |
| `redis:8.4.4` | `sha256:ac5c39529eb8b3e41318154581dad015b1f414e63d532f9318d25409a47f8451` | `sha256:c17daa76f4f44878637398c9d1b0a1f3c1d30d45a804c5bcdefa4f89e48b6f3b` |
| `nacos/nacos-server:v3.1.1` | `sha256:13e74786507abbacebe080fae2fc9c05aa2591eb99c97a0a78ff0e72beb21b13` | `sha256:9e4d25248c4a60212829e6ee49350af3d2a2e2c2849c789e80bffd25870a7e79` |
| `pgvector/pgvector:0.8.5-pg17-bookworm` | `sha256:d2ef61f42ef767baa5a1475393303cc235bcd92febd9d7014eddb48b41f3bad0` | `sha256:815bf5378222044da3b34d98e6a5fdac37b15c428b67d09c7c2d90a038e597bf` |
| `apache/rocketmq:5.3.1` | `sha256:d2b231c1b9204129e4f4dd65ec1521c81b8d6826e5f1fe8daa521dab0db5bf16` | `sha256:6ad88a43aece60f4974238fa6ac7be50e6a7347d6ac223f4182534ff12b67a91` |
| `apache/seata-server:2.5.0` | `sha256:e66df08010eccd2b4b5e033b4a075a4e184dcb969efa003f2d0082a1c586d684` | `sha256:9e5fa6c2b6e6c5a70a1d478a568def7e42d941d253e46f27e6601b37581d61c0` |
| `docker.elastic.co/elasticsearch/elasticsearch:8.19.17` | `sha256:aa6ee0ea2d708cea22a24ed44a420c5bbde9853d58a872e1e870f2086d04f652` | 单平台镜像，RepoDigest 同左 |

Registry mirrors are transport accelerators only. After pull, compare `docker image inspect` repo digests with this lock before starting services.
