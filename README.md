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
  "abbreviate-after": INT,
  "remove-fillers": [
    FILLER_WORD1,
    FILLER_WORD2,
    ...
  ]
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

If `remove-fillers` is specified, the value is a list of filler words which will
be removed from the transcript. Some common filler words in English are "um" and
"uh".

Neither option need be specified, in which case the formatter will default to
OTR, no abbreviation will be done, and no filler word removal will be done.

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
