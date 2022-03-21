package network.balanced.score.token.util;

import java.util.Map;

import score.ArrayDB;
import score.DictDB;

public class DBCollections {

	public static <T> Boolean containsInArrayDb(T value, ArrayDB<T> arraydb) {
		boolean found = false;
		if(arraydb == null || value == null) {
			return found;
		}

		for(int i = 0; i< arraydb.size(); i++) {
			if(arraydb.get(i) != null
					&& arraydb.get(i).equals(value)) {
				found = true;
				break;
			}
		}
		return found;
	}

	public static <T> T getFromArrayDb(T value, ArrayDB<T> arraydb) {
		if(arraydb == null || value == null) {
			return null;
		}

		for(int i = 0; i< arraydb.size(); i++) {
			T item = arraydb.get(i);
			if(item != null
					&& item.equals(value)) {
				return item;
			}
		}
		return null;
	}

	public static <K,V> Map<K,V> arrayAndDictDbToMap(ArrayDB<K> keys, DictDB<K, V> dictDb){
		int size = keys.size();

		@SuppressWarnings("unchecked")
		Map.Entry<K,V>[] addresses = new Map.Entry[size];

		for (int i= 0; i< size; i++ ) {
			K item = keys.get(i);
			V address = dictDb.get(item);
			addresses[i] = Map.entry(item, address);
		}
		return Map.<K,V>ofEntries(addresses);
	}
}
