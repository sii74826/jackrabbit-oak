/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.spi.security.authentication.token;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.jcr.Credentials;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregates a collection of {@link TokenProvider}s into a single
 * provider.
 */
public final class CompositeTokenProvider implements TokenProvider {

    private final List<TokenProvider> providers;

    private CompositeTokenProvider(@NotNull List<? extends TokenProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @NotNull
    public static TokenProvider newInstance(@NotNull TokenProvider... providers) {
        return newInstance(Arrays.<TokenProvider>asList(providers));
    }

    @NotNull
    public static TokenProvider newInstance(@NotNull List<? extends TokenProvider> providers) {
        switch (providers.size()) {
            case 0: return NULL_PROVIDER;
            case 1: return providers.iterator().next();
            default: return new CompositeTokenProvider(providers);
        }
    }

    @Override
    public boolean doCreateToken(@NotNull Credentials credentials) {
        for (TokenProvider tp : providers) {
            if (tp.doCreateToken(credentials)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public TokenInfo createToken(@NotNull Credentials credentials) {
        for (TokenProvider tp : providers) {
            TokenInfo info = tp.createToken(credentials);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public TokenInfo createToken(@NotNull String userId, @NotNull Map<String, ?> attributes) {
        for (TokenProvider tp : providers) {
            TokenInfo info = tp.createToken(userId, attributes);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public TokenInfo getTokenInfo(@NotNull String token) {
        for (TokenProvider tp : providers) {
            TokenInfo info = tp.getTokenInfo(token);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    private static final TokenProvider NULL_PROVIDER = new TokenProvider() {
        @Override
        public boolean doCreateToken(@NotNull Credentials credentials) {
            return false;
        }

        @Nullable
        @Override
        public TokenInfo createToken(@NotNull Credentials credentials) {
            return null;
        }

        @Nullable
        @Override
        public TokenInfo createToken(@NotNull String userId, @NotNull Map<String, ?> attributes) {
            return null;
        }

        @Nullable
        @Override
        public TokenInfo getTokenInfo(@NotNull String token) {
            return null;
        }
    };
}
