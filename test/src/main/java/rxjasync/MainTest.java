package rxjasync;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import java.io.IOException;

public class MainTest {
    private OkHttpClient mHttpClient = new OkHttpClient();

    private Response execute(String url) {
        Request request = new Request.Builder().url(url).build();
        try {
            return mHttpClient.newCall(request).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readString(Response response) {
        try {
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //fails with InterruptedIOException
    //if we are reading string in same method, where request is executed, no problem appears
    //exception is thrown because after completing Func0, internally subscription is unsubscribed
    //and interrupted exception is thrown to that thread, and OkHttp checks for interrupted.
    @Test
    public void testAsync() {
        Async.toAsync(new Func0<Response>() {
            @Override
            public Response call() {
                return execute("http://google.ru");
            }
        }, Schedulers.computation())
          .call()
          .map(new Func1<Response, String>() {
              @Override
              public String call(Response response) {
                  return readString(response);
              }
          })
          .concatMap(new Func1<String, Observable<?>>() {
              @Override
              public Observable<?> call(String s) {
                  return Async.toAsync(new Func0<Response>() {
                      @Override
                      public Response call() {
                          return execute("http://yandex.ru");
                      }
                  }).call().map(new Func1<Response, String>() {
                      @Override
                      public String call(Response response) {
                          return readString(response);
                      }
                  });
              }
          })
          .observeOn(Schedulers.immediate())
          .toBlocking()
          .single();
    }

    @Test
    public void testSubscriber() {
        Observable.create(new Observable.OnSubscribe<Response>() {
            @Override
            public void call(Subscriber<? super Response> subscriber) {
                Response r = execute("http://google.com");
                subscriber.onNext(r);
                subscriber.onCompleted();
            }
        }).subscribeOn(Schedulers.computation())
          .map(new Func1<Response, String>() {
              @Override
              public String call(Response response) {
                  return readString(response);
              }
          })
          .concatMap(new Func1<String, Observable<?>>() {
              @Override
              public Observable<?> call(String s) {
                  return Observable.create(new Observable.OnSubscribe<Response>() {
                      @Override
                      public void call(Subscriber<? super Response> subscriber) {
                          subscriber.onNext(execute("http://yandex.com"));
                          subscriber.onCompleted();
                      }
                  })
                    .map(new Func1<Response, String>() {
                        @Override
                        public String call(Response response) {
                            return readString(response);
                        }
                    });
              }
          })
          .observeOn(Schedulers.immediate())
          .toBlocking()
          .single();
    }

    private static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
            //org.junit.Assert.fail();
        }
    }
}
