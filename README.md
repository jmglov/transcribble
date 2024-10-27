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
  "capitalise": [
    CAPITALISE_WORD_1,
    CAPITALISE_WORD_2,
    ...
  ],
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
  "remove-active-listening": [
    WORD1,
    WORD2,
    ...
  ],
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

If `remove-active-listening` is specified, the value is a list of words which
indicate active listening; e.g. "mm", "right", "yeah", etc. Paragraphs
consisting solely of active listening words will be removed from the resulting
transcript. For example, given the following config:

``` json
"remove-active-listening": ["mm", "right", "yeah"]
```

And a transcript consisting of the following paragraphs:

``` text
[12:34] Speaker 1: It's vital that we not cede the terrain of struggle.
[12:38] Speaker 2: Right, yeah.
[12:39] Speaker 1: Do you know what I mean? We really have to move on
[12:42] Speaker 2: Mm. Mm. Yeah.
[12:43] Speaker 1: from this way of moving through the world.
```

The resulting transcript will look like this:

``` text
[12:34] Speaker 1: It's vital that we not cede the terrain of struggle.
[12:39] Speaker 1: Do you know what I mean? We really have to move on
[12:43] Speaker 1: from this way of moving through the world.
```

If `remove-fillers` is specified, the value is a list of filler words which will
be removed from the transcript. Some common filler words in English are "um" and
"uh". If `remove-fillers` is not specified, no filler word removal will be done.

If `remove-repeated-words` is specified, the value is a list of commonly
repeated words that will be stripped of repetition. For example, given the
following config:

``` json
"remove-repeated-words": ["and", "it's", "the"]
```

And a transcript containing a passage like this:

``` text
So it's it's it's important to remember the the... the themes and and words
```

The resulting passage will look like this:

``` text
So it's important to remember the themes and words
```

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

## Command-line interface

Add a `bb.edn` to a directory where you want to use Transcribble like this:

``` clojure
{:deps {jmglov/transcribble {:local/root "/path/to/transcribble/cli"}}
 :tasks
 {:requires ([babashka.fs :as fs]
             [transcribble.cli :as cli])
  :init (def opts {:aws-region "eu-west-1"
                   :s3-bucket "YOUR_BUCKET_HERE"
                   :s3-media-path (fs/file-name (fs/cwd))
                   :media-separator "_"
                   :language-code "en-US"})

  transcribble {:doc "Transcribe all the things!"
                :task (cli/dispatch opts)}
  }}
```

Then you can run commands like this:

``` text
bb transcribble --help
Usage: bb transcribble <subcommand> <options>

Subcommands:

  init-job: Set up files for transcription
    --media-file <file> Media file to transribe
  start-job: Start transcription job
    --media-file    <file>           Media file to transribe
    --language-code <lang>     en-US IETF language tag
    --speakers      <speakers>       List of speakers
  job-status: Get transcription job status
    --media-file <file> Media file being transcribed
  download-transcript: Download completed transcript
    --media-file <file> Media file being transcribed
  convert-transcript: Convert downloaded transcript
    --media-file <file>     Media file which has been transcribed
    --speakers   <speakers> List of speakers
  process-file: Initialise and start job, then download and convert transcript
    --media-file <file>     Media file to transribe
    --speakers   <speakers> List of speakers

Options:

Aws config
  --aws-region          <region-name> eu-west-1             AWS region
  --s3-bucket           <bucket-name> misc.jmglov.net       S3 bucket in which to store media files and transcripts
  --s3-media-path       <prefix>      Techs_Looming_Threats S3 prefix for media files
  --s3-transcripts-path <prefix>                            S3 prefix for transcript files (defaults to s3-media-path)

Basic config
  --media-separator <string> _ Replace spaces in media files with this string
```
