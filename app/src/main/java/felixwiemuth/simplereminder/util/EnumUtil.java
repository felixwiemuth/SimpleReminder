/*
 * Copyright (C) 2019 Felix Wiemuth
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package felixwiemuth.simplereminder.util;

import android.content.Intent;

/**
 * Enum utility for serializing/deserializing enums to/from intents. Based on https://stackoverflow.com/a/9753178/905686.
 */
public final class EnumUtil {
    public static class Serializer<T extends Enum<T>> extends Deserializer<T> {
        private T victim;

        @SuppressWarnings("unchecked")
        public Serializer(T victim) {
            super((Class<T>) victim.getClass());
            this.victim = victim;
        }

        public void to(Intent intent) {
            intent.putExtra(name, victim.ordinal());
        }
    }

    public static class Deserializer<T extends Enum<T>> {
        protected Class<T> victimType;
        protected String name;

        public Deserializer(Class<T> victimType) {
            this.victimType = victimType;
            this.name = victimType.getName();
        }

        public T from(Intent intent) {
            if (!intent.hasExtra(name)) throw new IllegalStateException();
            return victimType.getEnumConstants()[intent.getIntExtra(name, -1)];
        }
    }

    public static <T extends Enum<T>> Deserializer<T> deserialize(Class<T> victim) {
        return new Deserializer<T>(victim);
    }

    public static <T extends Enum<T>> Serializer<T> serialize(T victim) {
        return new Serializer<T>(victim);
    }
}
