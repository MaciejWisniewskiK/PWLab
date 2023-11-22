/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.Map;
import java.util.HashMap;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;

/*
Jeśli konfiguracja systemu dostarczona jako argumenty tej metody jest niepoprawna 
(np. jakiś komponent jest przypisany do urządzenia bez podanej pojemności lub liczba 
komponentów przypisanych do jakiegoś urządzenia przekracza jego pojemność), to metoda powinna 
podnieść wyjątek java.lang.IllegalArgumentException z odpowiednim komunikatem tekstowym.
*/

public final class StorageSystemFactory {

    public static StorageSystem newSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        

        if (deviceTotalSlots.isEmpty())
            throw new IllegalArgumentException("deviceTotalSlots map is empty");
        
        for (Integer totalSlots : deviceTotalSlots.values()) {
            if (totalSlots <= 0)
                throw new IllegalArgumentException("device has a non positive capacity");
        }

        Map<DeviceId, Integer> dts_copy = new HashMap(deviceTotalSlots);
        for (Map.Entry<ComponentId, DeviceId> entry : componentPlacement.entrySet()) {
            if (!deviceTotalSlots.containsKey(entry.getValue())) 
                throw new IllegalArgumentException("didn't specify a devices capacity");
            
            dts_copy.put(entry.getValue(), dts_copy.get(entry.getValue()) - 1);
            
            if (dts_copy.get(entry.getValue()) < 0)
                throw new IllegalArgumentException("too many components on a device");
        }

        return new MyStorageSystem(deviceTotalSlots, componentPlacement);
    }

}
