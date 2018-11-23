/**
 * Copyright 2018 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.redisson.RedissonList;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListReactive<V> {

    private final RedissonList<V> instance;

    public RedissonListReactive(CommandReactiveExecutor commandExecutor, String name) {
        this.instance = new RedissonList<V>(commandExecutor, name, null);
    }

    public RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.instance = new RedissonList<V>(codec, commandExecutor, name, null);
    }
    
    public Publisher<V> descendingIterator() {
        return iterator(-1, false);
    }

    public Publisher<V> iterator() {
        return iterator(0, true);
    }

    public Publisher<V> descendingIterator(int startIndex) {
        return iterator(startIndex, false);
    }

    public Publisher<V> iterator(int startIndex) {
        return iterator(startIndex, true);
    }

    private Publisher<V> iterator(final int startIndex, final boolean forward) {
        return new Stream<V>() {

            @Override
            public void subscribe(final Subscriber<? super V> t) {
                t.onSubscribe(new ReactiveSubscription<V>(this, t) {

                    private int currentIndex = startIndex;

                    @Override
                    protected void onRequest(final long n) {
                        final ReactiveSubscription<V> m = this;
                        instance.getAsync(currentIndex).addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                if (!future.isSuccess()) {
                                    m.onError(future.cause());
                                    return;
                                }
                                
                                V value = future.getNow();
                                if (value != null) {
                                    m.onNext(value);
                                    if (forward) {
                                        currentIndex++;
                                    } else {
                                        currentIndex--;
                                    }
                                }
                                
                                if (value == null) {
                                    m.onComplete();
                                    return;
                                }
                                if (n-1 == 0) {
                                    return;
                                }
                                onRequest(n-1);
                            }
                        });
                    }
                });
            }

        };
    }
    
    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {

            @Override
            public RFuture<Boolean> add(Object o) {
                return instance.addAsync((V)o);
            }

        }.addAll(c);
    }

}
