package network.balanced.score.token.util;

import java.util.Map;

public final class Collections {

	public static <E,K> E getEntryOrDefault(K key, E defaultValue, Map<K,E> map) {
		E entry = map.get(key);

		if(entry == null) {
			return defaultValue;
		}
		return entry;
	}
}
