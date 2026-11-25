package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaceHubKeyStoreTest {

    @TempDir
    Path dir;

    private TokenCipher cipher() {
        TokenCipher cipher = mock(TokenCipher.class);
        when(cipher.encrypt(anyString())).thenAnswer(i -> new StringBuilder((String) i.getArgument(0)).reverse().toString());
        when(cipher.decrypt(anyString())).thenAnswer(i -> new StringBuilder((String) i.getArgument(0)).reverse().toString());
        return cipher;
    }

    @Test
    void aSavedKeyIsKeptEncryptedAndReadBackByANewStore() throws Exception {
        Path file = dir.resolve(RaceHubKeyStore.FILE_NAME);
        new RaceHubKeyStore(file, cipher()).save("secret-key-1");

        assertThat(Files.readString(file)).doesNotContain("secret-key-1");
        assertThat(new RaceHubKeyStore(file, cipher()).saved()).contains("secret-key-1");
    }

    @Test
    void savingAgainReplacesTheKeyAndClearingRemovesIt() {
        Path file = dir.resolve(RaceHubKeyStore.FILE_NAME);
        RaceHubKeyStore store = new RaceHubKeyStore(file, cipher());
        store.save("first-key-111");
        store.save("second-key-22");
        assertThat(store.saved()).contains("second-key-22");

        store.clear();
        assertThat(store.saved()).isEmpty();
        assertThat(file).doesNotExist();
    }

    @Test
    void aKeyThatCannotBeDecryptedIsIgnored() throws Exception {
        Path file = dir.resolve(RaceHubKeyStore.FILE_NAME);
        Files.writeString(file, "garbage");
        TokenCipher cipher = mock(TokenCipher.class);
        when(cipher.decrypt(anyString())).thenThrow(new TokenCipher.UnreadableTokenException());

        assertThat(new RaceHubKeyStore(file, cipher).saved()).isEmpty();
    }

    @Test
    void theEndpointUsesTheSavedKeyInPlaceOfTheSettingsFilesAtOnce() {
        ClubProfileService profile = mock(ClubProfileService.class);
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        RaceHubKeyStore store = new RaceHubKeyStore(dir.resolve(RaceHubKeyStore.FILE_NAME), cipher());
        RaceHubResultsEndpoint endpoint = new RaceHubResultsEndpoint(
                new RaceHubResultsProperties(URI.create("https://racehub.example/api/results"), "file-key"), profile, store);

        assertThat(endpoint.token()).isEqualTo("file-key");
        store.save("rotated-key-1");
        assertThat(endpoint.token()).isEqualTo("rotated-key-1");
        assertThat(endpoint.keySaved()).isTrue();
        store.clear();
        assertThat(endpoint.token()).isEqualTo("file-key");
        assertThat(endpoint.isRaceHub(URI.create("https://racehub.example/api/rctc/v1/events"))).isTrue();
        assertThat(endpoint.isRaceHub(URI.create("https://other.example/x"))).isFalse();
    }
}
