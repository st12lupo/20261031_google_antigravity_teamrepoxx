---
name: start-task
description: TASKS.md のタスク(T-01a や T-04 など)に着手するときに使う。タスクの範囲、依存、完了条件を確認してブランチを作る。
---

# タスクの開始

1. `TASKS.md` から指定のタスクを探し、内容、担当するファイル、完了条件、依存を確認する。タスクの ID が指定されていなければ、人に確認する。
2. 依存するタスクが `main` にマージ済みか確認する。未完了なら、仮の実装で進められるかを `TASKS.md` で確認する。進められなければ、作業を始めずに人に伝える。
3. `REQUIREMENTS.md` から関係する要件を読む。実装する要件の ID を控える。
4. 最新の `main` からブランチを作る。

   ```bash
   git switch main
   git pull --ff-only
   git switch -c feature/t-04-receipt-parser
   ```

   名前は `feature/<タスク ID の小文字>-<内容>` とする。

5. `git config user.email` が noreply アドレスか確認する。個人のアドレスなら、コミットせずに人に伝える。
6. 実装の前に、次を人に短く伝える。
   - 実装する要件の ID
   - 変更するファイル
   - 完了条件の確認方法

## PR を作るとき
- 差分が担当ファイルの中だけか確認する: `git diff --stat main...HEAD`
- PR には、タスク ID、要件の ID、実行した確認とその結果、未確認の項目を書く
- マージは人に任せる
