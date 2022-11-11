package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlutusDataJsonConverterTest {

    @Test
    void serDeser1() throws Exception {
        String datumHex = "d8799fd8799f4040ffd8799f581ca0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c23545484f534b59ff1b000000e449901987181eff";
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        String json = PlutusDataJsonConverter.toJson(plutusData);
        System.out.println(json);

        PlutusData dePlutusData = PlutusDataJsonConverter.toPlutusData(json);
        assertThat(dePlutusData).isEqualTo(plutusData);
    }

    @Test
    void serDeser2() throws Exception {
        String datumHex = "d8799fd8799fd8799fd8799f581c33163652e747a54b16680371b7b6812d355a1dd1087239bfaf57d2f3ffd8799fd8799fd8799f581c1ee198ba32b1d7abe754cc38075471d7a322b72b624e63e56109fe8effffffff581ca0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c23545484f534b5940401a29d8e284d87a801a00286f90ffff";
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        String json = PlutusDataJsonConverter.toJson(plutusData);
        System.out.println(json);

        PlutusData dePlutusData = PlutusDataJsonConverter.toPlutusData(json);
        System.out.println(PlutusDataJsonConverter.toJson(dePlutusData));

        String serHex = dePlutusData.serializeToHex();
        assertThat(serHex).isEqualTo(datumHex);

    }

    @Test
    void serDeser_with_map() throws Exception {
        String datumHex = "d8799f581cdf078188aedb870e1161440a90c6c29bc6e9732054a1aa59bd440c2a9fd8799fd8799fd8799f581c1ed92de403227c0fad57b35c8337557234a3863d54294a834b6e95a3ffd8799fd8799fd8799f581cf4627cc3236d1ec8e948ea05b8c8381a2e10ec9a2df4dd2bf6bf084affffffffa140d8799f00a1401a019b3970ffffd8799fd8799fd8799f581c70e60f3b5ea7153e0acc7a803e4401d44b8ed1bae1c7baaad1a62a72ffd8799fd8799fd8799f581c1e78aae7c90cc36d624f7b3bb6d86b52696dc84e490f343eba89005fffffffffa140d8799f00a1401a00a47d60ffffd8799fd8799fd8799f581cdf078188aedb870e1161440a90c6c29bc6e9732054a1aa59bd440c2affd8799fd8799fd8799f581c581d95b6e83eb838560b2d71ee9af38ee347bfe3b577791391525d7effffffffa140d8799f00a1401a1de0c5f0ffffffff";
        PlutusData plutusData = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        String json = PlutusDataJsonConverter.toJson(plutusData);
        System.out.println(json);

        PlutusData dePlutusData = PlutusDataJsonConverter.toPlutusData(json);
        System.out.println(PlutusDataJsonConverter.toJson(dePlutusData));

        String serHex = dePlutusData.serializeToHex();
        assertThat(serHex).isEqualTo(datumHex);
    }
}
