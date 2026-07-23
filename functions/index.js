const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

// Notifies the other chat member when a new video message lands.
exports.notifyNewMessage = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{messageId}",
    region: "europe-west1",
  },
  async (event) => {
    const message = event.data.data();
    const { chatId } = event.params;
    const senderId = message.senderId;

    const chatSnap = await admin.firestore().doc(`chats/${chatId}`).get();
    if (!chatSnap.exists) return;
    const chat = chatSnap.data();

    const recipientId = (chat.members || []).find((uid) => uid !== senderId);
    if (!recipientId) return;

    const recipientSnap = await admin
      .firestore()
      .doc(`users/${recipientId}`)
      .get();
    const tokens = recipientSnap.get("fcmTokens") || [];
    if (tokens.length === 0) return;

    const senderInfo = (chat.memberInfo || {})[senderId] || {};
    const senderName = senderInfo.displayName || senderInfo.username || "WeVid";

    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: senderName,
        body: "Sent you a video message",
      },
      data: {
        chatId,
        chatTitle: senderName,
      },
      android: {
        priority: "high",
        notification: {
          channelId: "messages",
        },
      },
    });

    // Prune tokens that are no longer valid (uninstalled / rotated).
    const invalid = [];
    response.responses.forEach((res, i) => {
      const code = res.error && res.error.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-argument"
      ) {
        invalid.push(tokens[i]);
      }
    });
    if (invalid.length > 0) {
      await recipientSnap.ref.update({
        fcmTokens: admin.firestore.FieldValue.arrayRemove(...invalid),
      });
    }
  }
);
