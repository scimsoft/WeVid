# Firestore & Storage security rules

The live rules are `firestore.rules` and `storage.rules` in the repo root and deploy with
`npx firebase-tools deploy --only firestore:rules,storage --project wevid-a43ef`.
The copies below are kept for reference; additions since the initial version:
`users/{uid}/blocks` (owner-only block list) and `reports` (write-only UGC reports).

## Firestore (Firestore Database → Rules)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == uid;
    }

    // Username reservations: one doc per username, owned by the claimant.
    match /usernames/{username} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.uid == request.auth.uid;
      allow update, delete: if request.auth != null
        && resource.data.uid == request.auth.uid;
    }

    match /chats/{chatId} {
      // `get` must also pass when the doc doesn't exist yet (resource == null),
      // because the app checks for an existing chat before creating it.
      allow get: if request.auth != null
        && (resource == null || request.auth.uid in resource.data.members);
      allow list: if request.auth != null
        && request.auth.uid in resource.data.members;
      allow create: if request.auth != null
        && request.auth.uid in request.resource.data.members;
      allow update: if request.auth != null
        && request.auth.uid in resource.data.members;

      match /messages/{messageId} {
        allow read: if request.auth != null
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.members;
        allow create: if request.auth != null
          && request.resource.data.senderId == request.auth.uid
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.members;
        allow update: if request.auth != null
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.members;
      }
    }

    // Location-dropped clips on the nearby feed.
    match /posts/{postId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.authorId == request.auth.uid
        && request.resource.data.keys().hasAll([
          'authorId', 'authorInfo', 'videoUrl', 'durationMs',
          'createdAt', 'lat', 'lng', 'geohash'
        ]);
      allow update, delete: if request.auth != null
        && resource.data.authorId == request.auth.uid;
    }
  }
}
```

## Storage (Storage → Rules)

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /videos/{chatId}/{fileName} {
      allow read: if request.auth != null
        && request.auth.uid in firestore.get(/databases/(default)/documents/chats/$(chatId)).data.members;
      allow write: if request.auth != null
        && request.auth.uid in firestore.get(/databases/(default)/documents/chats/$(chatId)).data.members
        && request.resource.size < 100 * 1024 * 1024;
    }

    match /posts/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null
        && request.resource.size < 100 * 1024 * 1024;
    }
  }
}
```

Notes:

- Storage rules referencing Firestore (`firestore.get`) require rules_version 2 and work on all current Firebase projects.
- The 100 MB cap is generous for ≤30s clips; tighten later if needed.
- Message updates are member-writable because recipients append their uid to `seenBy`.
- Feed posts are readable by any signed-in user; create is limited to `authorId == auth.uid`.
- After publishing these rules, create a single-field index is usually not needed for the
  geohash range queries (`orderBy("geohash")`), but if the console prompts for one, accept it.
