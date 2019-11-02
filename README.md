# Transcribble

Converts [Amazon Transcribe](https://docs.aws.amazon.com/transcribe/latest/dg/what-is-transcribe.html) output to other transcript formats (plain text, [oTranscribe](https://otranscribe.com) OTR, etc).

## Running Transcribble

```sh
clojure -m transcribble.main AMAZON_TRANSCRIBE_JSON OUTPUT_FILE AUDIO_FILE SPEAKERS [CONFIG_FILE]
```

## Configuration

The config file is a JSON file containing the following options:

```json
{
  "formatter": FORMATTER,
  "abbreviate-after": INT
}
```

Valid values for `FORMATTER` are:
- `"otr"`: [oTranscribe](https://otranscribe.com) format
- `"plaintext"`: plain text

If `abbreviate-after` is specified, the value is the number of speaker
alterations after which to start abbreviating speaker names. Speaker names will
be abbreviated by initials, unless doing so is ambiguous (e.g. you have a
speaker named "Kim Gates" and another named "Kaia Gerbini", so both would be
abbreviated "KG", which is not helpful); otherwise the first part of their name,
unless that too is ambiguous (e.g. "Cher Binnington" and "Cher Tjeba");
otherwise their full name.

Neither option need be specified, in which case the formatter will default to
OTR and no abbreviation will be done.
