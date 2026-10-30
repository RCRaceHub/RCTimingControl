import { GuideLinks } from './GuideLinks';

export function DecoderHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Decoder page tells RCTC where the AMB decoder is on the venue network. RCTC connects to
        it directly, and reconnects on its own if the link drops. Race control shows whether the
        decoder is connected in its status bar.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Decoder Host:</span> The decoder&apos;s IP address on the venue network, or &quot;localhost&quot; when you are trying the app out with the simulator.</li>
        <li><span className="font-semibold">Protocol:</span> Choose RC4 for decoders with firmware below 4.5, which is what most clubs have. The port fills in from the protocol; change it only if your decoder uses another.</li>
        <li><span className="font-semibold">Test Connection:</span> Click it after entering the details. It tries what is typed in the form, whether or not it is saved yet, and reports Connected once the decoder is sending.</li>
        <li><span className="font-semibold">Save:</span> Click &quot;Save&quot; to keep the details. A test on its own does not save them.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          Never connect RCTC and another timing program (such as RCResults) to the same decoder at
          the same time; one of them may quietly stop receiving laps. Close the other program
          first. The decoder guide has more on hardware and the simulator.
        </p>
      </div>

      <GuideLinks admin doc="decoder" />
    </div>
  );
}
