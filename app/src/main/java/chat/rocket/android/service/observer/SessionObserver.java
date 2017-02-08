package chat.rocket.android.service.observer;

import android.content.Context;
import io.realm.Realm;
import io.realm.RealmResults;

import java.util.List;
import chat.rocket.android.RocketChatCache;
import chat.rocket.android.api.RaixPushHelper;
import chat.rocket.android.helper.LogcatIfError;
import chat.rocket.android.model.internal.GetUsersOfRoomsProcedure;
import chat.rocket.android.model.internal.LoadMessageProcedure;
import chat.rocket.android.model.internal.MethodCall;
import chat.rocket.android.model.internal.Session;
import chat.rocket.persistence.realm.RealmHelper;
import chat.rocket.android.service.DDPClientRef;
import chat.rocket.android.service.internal.StreamRoomMessageManager;
import hugo.weaving.DebugLog;

/**
 * Observes user is logged into server.
 */
public class SessionObserver extends AbstractModelObserver<Session> {
  private final StreamRoomMessageManager streamNotifyMessage;
  private final RaixPushHelper pushHelper;
  private int count;

  /**
   * constructor.
   */
  public SessionObserver(Context context, String hostname,
                         RealmHelper realmHelper, DDPClientRef ddpClientRef) {
    super(context, hostname, realmHelper, ddpClientRef);
    count = 0;

    streamNotifyMessage = new StreamRoomMessageManager(context, hostname, realmHelper, ddpClientRef);
    pushHelper = new RaixPushHelper(realmHelper, ddpClientRef);
  }

  @Override
  public RealmResults<Session> queryItems(Realm realm) {
    return realm.where(Session.class)
        .isNotNull(Session.TOKEN)
        .equalTo(Session.TOKEN_VERIFIED, true)
        .isNull(Session.ERROR)
        .findAll();
  }

  @Override
  public void onUpdateResults(List<Session> results) {
    int origCount = count;
    count = results.size();
    if (origCount > 0 && count > 0) {
      return;
    }

    if (count == 0) {
      if (origCount > 0) {
        onLogout();
      }
      return;
    }

    if (origCount == 0 && count > 0) {
      onLogin();
    }
  }

  @DebugLog
  private void onLogin() {
    streamNotifyMessage.register();

    // update push info
    pushHelper
        .pushSetUser(RocketChatCache.getOrCreatePushId(context))
        .continueWith(new LogcatIfError());
  }

  @DebugLog
  private void onLogout() {
    streamNotifyMessage.unregister();

    realmHelper.executeTransaction(realm -> {
      // remove all tables. ONLY INTERNAL TABLES!.
      realm.delete(MethodCall.class);
      realm.delete(LoadMessageProcedure.class);
      realm.delete(GetUsersOfRoomsProcedure.class);
      return null;
    }).continueWith(new LogcatIfError());
  }
}
