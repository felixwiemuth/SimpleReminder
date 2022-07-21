/*
 * Copyright (C) 2018-2022 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

/**
 * Indicates that the application is in a state which is illegal according to its design. This is
 * probably caused by an implementation error.
 *
 * @author Felix Wiemuth
 */
public class ImplementationError extends RuntimeException {
    public ImplementationError() {
    }

    public ImplementationError(String message) {
        super(message);
    }

    public ImplementationError(String message, Throwable cause) {
        super(message, cause);
    }

    public ImplementationError(Throwable cause) {
        super(cause);
    }
}
