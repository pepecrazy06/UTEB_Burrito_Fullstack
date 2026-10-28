package vn.uteb;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class StoreCoordinatesTest {
    @Test void storesCoordinatesAndPreservesThemForOldClients() {
        Domain.Store s=new Domain.Store();
        ShopService.updateStoreCoordinates(s,Map.of("latitude","10.85","longitude","106.77"));
        ShopService.updateStoreCoordinates(s,Map.of("name","UTEB"));
        assertThat(s.latitude).isEqualTo(10.85);assertThat(s.longitude).isEqualTo(106.77);
    }
    @Test void emptyPairClearsTheMapPosition() {
        Domain.Store s=new Domain.Store();s.latitude=10.85;s.longitude=106.77;
        ShopService.updateStoreCoordinates(s,Map.of("latitude","","longitude",""));
        assertThat(s.latitude).isZero();assertThat(s.longitude).isZero();
    }
    @ParameterizedTest @CsvSource({"NaN,106","10,Infinity","86,106","10,181","abc,106"})
    void rejectsInvalidCoordinatesWithoutChangingThePosition(String lat,String lon) {
        Domain.Store s=new Domain.Store();s.latitude=10.85;s.longitude=106.77;
        assertThatThrownBy(()->ShopService.updateStoreCoordinates(s,Map.of("latitude",lat,"longitude",lon)))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(s.latitude).isEqualTo(10.85);assertThat(s.longitude).isEqualTo(106.77);
    }
    @Test void rejectsAnIncompletePair() {
        assertThatThrownBy(()->ShopService.updateStoreCoordinates(new Domain.Store(),Map.of("latitude","10.85")))
            .isInstanceOf(ResponseStatusException.class);
    }
}
