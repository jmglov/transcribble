# Transcribble

Converts [Amazon Transcribe](https://docs.aws.amazon.com/transcribe/latest/dg/what-is-transcribe.html) output to other transcript formats (plain text, [oTranscribe](https://otranscribe.com) OTR, etc).

## Running Transcribble

### Convert JSON to OTR

```sh
clojure -m transcribble.main AMAZON_TRANSCRIBE_JSON OUTPUT_FILE AUDIO_FILE SPEAKERS [CONFIG_FILE]
```

### Fixup OTR

Remove empty lines.

```sh
clojure -m transcribble.main --fixup-otr OTR_FILE
```

### Convert OTR to PDF

```sh
clojure -m transcribble.main --convert-otr OTR_FILE PDF_FILE TITLE [CONFIG_FILE]
```

## Paragraph splitting

When there are multiple speakers, a new paragraph will begin every time the
speaker changes. Otherwise, paragraphs will be split after a certain number of
seconds; see the `split-duration-secs` [configuration option](#configuration)
below.

## Configuration

The config file is a JSON file containing the following options:

```json
{
  "abbreviate-after": INT,
  "abbreviator": ABBREVIATOR,
  "downcase": [
    DOWNCASE_WORD1,
    DOWNCASE_WORD2,
    ...
  ],
  "formatter": FORMATTER,
  "pdf": {
    "styles": {
      PDF_DOCUMENT_ELEMENT1: { ... },
      PDF_DOCUMENT_ELEMENT2: { ... },
      ...
    }
  },
  "remove-fillers": [
    FILLER_WORD1,
    FILLER_WORD2,
    ...
  ],
  "replace": {
    REGEX1: REPLACEMENT1,
    REGEX2: REPLACEMENT2,
    ...
  },
  "split-duration-secs": INT
}
```

If `abbreviate-after` is specified, the value is the number of speaker
alterations after which to start abbreviating speaker names. If
`abbreviate-after` is not specified, no abbreviation will be done.

Valid values for `ABBREVIATOR` are:
- `"first-name-or-title"`: [DEFAULT] abbreviate by the first part of their name
  (or title, if present); e.g. "Cher Binnington" would become "Cher", "Davis
  Potter" would become "Davis", and "Dr. Courtney Bennet" would become "Dr.
  Bennet". If abbreviating by first name or title is ambiguous, the full names
  of **all speakers** will be used; e.g. "Cher Binnington", "Cher Tjeba", and
  "Dr. Courtney Bennet" should be "Cher", "Cher", and "Dr. Bennet", but since
  the two people with the first name "Cher" are ambiguous, none of the speaker
  names will be abbreviated.
- `"initials"`: abbreviate by initials; e.g. "Cher Binnington" would become "CB"
  and "Cher Tjeba" would become "CT"

Valid values for `FORMATTER` are:
- `"otr"`: [DEFAULT] [oTranscribe](https://otranscribe.com) format
- `"plaintext"`: plain text
- `"paragraphs"`: list of paragraphs as strings

`pdf` is used to set options for PDF export in `--convert-otr` mode. Supported
options are:
- `"style"`: map of [PDF document
  elements](https://github.com/clj-pdf/clj-pdf#document-elements) to styles

If `remove-fillers` is specified, the value is a list of filler words which will
be removed from the transcript. Some common filler words in English are "um" and
"uh". If `remove-fillers` is not specified, no filler word removal will be done.

If `replace` is specified, the value is a map of regular expressions to their
replacements; e.g. `"([Ff])olks": "$1olx"` would replace all occurrences of
"Folks" or "folks" with "Folx" or "folx". If `replace` is not specified, no
replacements will be done.

If `split-duration-secs` is specified, the value is the number of seconds a
paragraph can continue for. Once `split-duration-secs` is reached, the paragraph
will be ended at the end of the current sentence and a new paragraph will be
started. If `split-duration-secs` is not specified, it defaults to 30.

## AWS Transcribe document format

```clj
{:jobName "some-job"
 :accountId "123456789"
 :status "COMPLETED"
 :results
 {:transcripts ["This is what they say in the podcast..."]
  :speaker_labels
  {:speakers 2  ; number of speakers
   :segments
   [{:start_time "11.91"
     :end_time "13.39"
     :speaker_label "spk_1"
     :items
     [{:start_time "11.91", :end_time "12.4", :speaker_label "spk_1"}
      {:start_time "12.41", :end_time "13.39", :speaker_label "spk_1"}]}]}
 :items
 [{:start_time "11.91",
   :end_time "12.4",
   :alternatives [{:confidence "1.0", :content "never"}],
   :type "pronunciation"}]}}
```

## Building

```bash
./bin/build
```
