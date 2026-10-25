/**
 * How to trust the club's certificate authority on each kind of device (#503). Installing it once makes every
 * HTTPS address of the app open without a warning; without it the browser asks to click through once.
 */
export function CertificateSteps() {
  return (
    <div className="space-y-2 text-sm">
      <details>
        <summary className="cursor-pointer font-medium">Windows</summary>
        <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
          <li>Download the certificate and open it.</li>
          <li>Choose Install Certificate, then Local Machine.</li>
          <li>Pick “Place all certificates in the following store” and choose Trusted Root Certification Authorities.</li>
          <li>Finish, then restart the browser.</li>
        </ol>
      </details>
      <details>
        <summary className="cursor-pointer font-medium">macOS</summary>
        <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
          <li>Download the certificate and double-click it to add it to Keychain Access.</li>
          <li>Open it there, expand Trust, and set “When using this certificate” to Always Trust.</li>
          <li>Restart the browser.</li>
        </ol>
      </details>
      <details>
        <summary className="cursor-pointer font-medium">iPhone and iPad</summary>
        <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
          <li>Open the download link in Safari and allow the profile download.</li>
          <li>Install it in Settings, under Profile Downloaded.</li>
          <li>Turn it on in Settings, General, About, Certificate Trust Settings.</li>
        </ol>
      </details>
      <details>
        <summary className="cursor-pointer font-medium">Android</summary>
        <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
          <li>Download the certificate.</li>
          <li>Open Settings, search for “CA certificate”, and install it from there (Security, Encryption and credentials).</li>
          <li>Restart the browser.</li>
        </ol>
      </details>
      <details>
        <summary className="cursor-pointer font-medium">Linux (Chrome or Edge)</summary>
        <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
          <li>Open chrome://settings/certificates, then Authorities, then Import.</li>
          <li>Pick the downloaded file and tick “Trust this certificate for identifying websites”.</li>
        </ol>
      </details>
    </div>
  );
}
