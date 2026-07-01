# Voice-clip render worklist

Every UNIQUE phrase to render, per style, EN + UK (cues + per-phase + praise).
Drop your rendered files in a folder, then either name each file after its phrase text,
or fill the matching `<styleId>.pairing.template.json` (copy it into the folder as `pairing.json`),
and run: `TTS_PROVIDER=files VOICE_SRC=<folder> npm run gen:voice -- <style>.voicestyle.json`.

## Playful (`preset-playful`) — 72 phrases

### EN — 36

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | give the elbow a little bend | `en__fn2ioi` |
| 2 | cue | elbow_angle | - | down | reach through it | `en__z72eny` |
| 3 | cue | shoulder_angle | - | up | drop the shoulder a touch | `en__4llm6n` |
| 4 | cue | shoulder_angle | - | down | open the shoulder a bit more | `en__3fgzct` |
| 5 | cue | knee_bend | - | up | sit into it, legs on | `en__9pyx4q` |
| 6 | cue | knee_bend | - | down | ease up, stand a little taller | `en__k8jdx9` |
| 7 | cue | torso_lean | - | up | stand a bit taller | `en__uiztqt` |
| 8 | cue | torso_lean | - | down | lean into the ball | `en__c7duxx` |
| 9 | cue | shoulder_tilt | - | up | level the shoulders | `en__4g353` |
| 10 | cue | hip_flexion | - | up | ease the hips up a touch | `en__712xf1` |
| 11 | cue | hip_flexion | - | down | sink into the hips | `en__1eu0ni5` |
| 12 | phaseCue | elbow_angle | backswing | up | don't lock the elbow on the backswing | `en__c8xtgl` |
| 13 | phaseCue | elbow_angle | backswing | down | open the elbow a little on the backswing | `en__1xax4dd` |
| 14 | phaseCue | elbow_angle | followthrough | up | finish the elbow through | `en__8h07wn` |
| 15 | phaseCue | elbow_angle | followthrough | down | don't over-fold the finish | `en__195s5az` |
| 16 | phaseCue | shoulder_angle | backswing | up | keep the arm low on the take-back | `en__mre0ko` |
| 17 | phaseCue | shoulder_angle | backswing | down | let the arm hang back a bit | `en__gey5pm` |
| 18 | phaseCue | shoulder_angle | followthrough | up | don't over-swing the finish | `en__169qym6` |
| 19 | phaseCue | shoulder_angle | followthrough | down | sweep up to finish | `en__1rpjuyk` |
| 20 | phaseCue | knee_bend | backswing | up | sit a little deeper as you load | `en__u2m5u` |
| 21 | phaseCue | knee_bend | backswing | down | don't over-sink the load | `en__1aejoq8` |
| 22 | phaseCue | knee_bend | contact | up | stay down through contact | `en__sc9ekz` |
| 23 | phaseCue | knee_bend | contact | down | don't over-bend at contact | `en__15xyilk` |
| 24 | phaseCue | hip_flexion | backswing | up | hinge into the hips a touch more | `en__7fn5by` |
| 25 | phaseCue | hip_flexion | backswing | down | ease the hips up as you load | `en__cv1b5q` |
| 26 | phaseCue | hip_flexion | contact | up | keep the hip hinge at contact | `en__18pbolq` |
| 27 | phaseCue | hip_flexion | contact | down | ease the hips up at contact | `en__4yeyfw` |
| 28 | phaseCue | torso_lean | backswing | up | stay a bit taller on the take-back | `en__1a6advn` |
| 29 | phaseCue | torso_lean | backswing | down | lean in a little as you load | `en__v6pbq6` |
| 30 | phaseCue | torso_lean | contact | up | stand a touch taller at contact | `en__1tzmfkq` |
| 31 | phaseCue | torso_lean | contact | down | lean into the ball at contact | `en__1tueki8` |
| 32 | praise | - | - | #1 | that's the shape! | `en__dtq1gv` |
| 33 | praise | - | - | #2 | yes — that follow-through | `en__11s6c0g` |
| 34 | praise | - | - | #3 | clean — do that again | `en__1lltxp2` |
| 35 | praise | - | - | #4 | nice, really solid | `en__1dv1n8a` |
| 36 | praise | - | - | #5 | love it, keep going | `en__g2989j` |

### UK — 36

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | трохи зігни лікоть | `uk__g3lqms` |
| 2 | cue | elbow_angle | - | down | тягнися крізь мʼяч | `uk__11f4n9r` |
| 3 | cue | shoulder_angle | - | up | ледь опусти плече | `uk__2sjuuh` |
| 4 | cue | shoulder_angle | - | down | трохи відкрий плече | `uk__k0uquk` |
| 5 | cue | knee_bend | - | up | присядь, працюй ногами | `uk__xefj9e` |
| 6 | cue | knee_bend | - | down | трохи випрямись | `uk__63bunr` |
| 7 | cue | torso_lean | - | up | тримайся трохи рівніше | `uk__187m4ev` |
| 8 | cue | torso_lean | - | down | нахились до мʼяча | `uk__1ds6yix` |
| 9 | cue | shoulder_tilt | - | up | вирівняй плечі | `uk__1w98xz4` |
| 10 | cue | hip_flexion | - | up | трохи вище стегнами | `uk__mfam4e` |
| 11 | cue | hip_flexion | - | down | присядь у стегнах | `uk__830bmi` |
| 12 | phaseCue | elbow_angle | backswing | up | не блокуй лікоть на замаху | `uk__111yc4e` |
| 13 | phaseCue | elbow_angle | backswing | down | трохи розправ лікоть на замаху | `uk__1w8nf9z` |
| 14 | phaseCue | elbow_angle | followthrough | up | доводь лікоть до кінця | `uk__18owvam` |
| 15 | phaseCue | elbow_angle | followthrough | down | не затискай на завершенні | `uk__1qllgx0` |
| 16 | phaseCue | shoulder_angle | backswing | up | тримай руку нижче на замаху | `uk__142i32p` |
| 17 | phaseCue | shoulder_angle | backswing | down | трохи опусти руку назад | `uk__mrpok9` |
| 18 | phaseCue | shoulder_angle | followthrough | up | не перемахуй на завершенні | `uk__f718vb` |
| 19 | phaseCue | shoulder_angle | followthrough | down | доводь руку вгору | `uk__1crmblp` |
| 20 | phaseCue | knee_bend | backswing | up | присядь трохи глибше на замаху | `uk__1baeiw9` |
| 21 | phaseCue | knee_bend | backswing | down | не присідай надто на замаху | `uk__jqd3t1` |
| 22 | phaseCue | knee_bend | contact | up | тримай присід в ударі | `uk__2je8p9` |
| 23 | phaseCue | knee_bend | contact | down | не перегинай коліна в ударі | `uk__1nmo98q` |
| 24 | phaseCue | hip_flexion | backswing | up | трохи більше нахилу в стегнах | `uk__1vuzsji` |
| 25 | phaseCue | hip_flexion | backswing | down | трохи розігни стегна на замаху | `uk__1iw4pvp` |
| 26 | phaseCue | hip_flexion | contact | up | тримай нахил стегон в ударі | `uk__xyhfyc` |
| 27 | phaseCue | hip_flexion | contact | down | розігни стегна в ударі | `uk__1k27oef` |
| 28 | phaseCue | torso_lean | backswing | up | тримайся рівніше на замаху | `uk__1jrj47q` |
| 29 | phaseCue | torso_lean | backswing | down | трохи нахились на замаху | `uk__dnb4wq` |
| 30 | phaseCue | torso_lean | contact | up | тримайся рівніше в ударі | `uk__1xt12d1` |
| 31 | phaseCue | torso_lean | contact | down | нахились до мʼяча в ударі | `uk__62wuga` |
| 32 | praise | - | - | #1 | оце форма! | `uk__1lgdqvz` |
| 33 | praise | - | - | #2 | так — оце завершення | `uk__xe4i12` |
| 34 | praise | - | - | #3 | чисто — ще раз так | `uk__qwl48w` |
| 35 | praise | - | - | #4 | гарно, дуже впевнено | `uk__4u30nw` |
| 36 | praise | - | - | #5 | клас, продовжуй | `uk__f5ujik` |

## Strict (`preset-strict`) — 66 phrases

### EN — 33

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | bend the elbow | `en__8d7a1u` |
| 2 | cue | elbow_angle | - | down | extend more | `en__k0sw2e` |
| 3 | cue | shoulder_angle | - | up | drop the shoulder | `en__kwywcr` |
| 4 | cue | shoulder_angle | - | down | open the shoulder | `en__1bmq2qe` |
| 5 | cue | knee_bend | - | up | bend the knees | `en__8ekbtz` |
| 6 | cue | knee_bend | - | down | stand taller | `en__xw0igb` |
| 7 | cue | torso_lean | - | down | lean in | `en__1edxc4` |
| 8 | cue | shoulder_tilt | - | up | level the shoulders | `en__4g353` |
| 9 | cue | hip_flexion | - | up | stand tall | `en__1ftj95o` |
| 10 | cue | hip_flexion | - | down | hinge forward | `en__g5s5qr` |
| 11 | phaseCue | elbow_angle | backswing | up | don't lock the elbow on the backswing | `en__c8xtgl` |
| 12 | phaseCue | elbow_angle | backswing | down | open the elbow on the backswing | `en__5n9lws` |
| 13 | phaseCue | elbow_angle | followthrough | up | finish the elbow through | `en__8h07wn` |
| 14 | phaseCue | elbow_angle | followthrough | down | don't over-fold the finish | `en__195s5az` |
| 15 | phaseCue | shoulder_angle | backswing | up | arm low on the backswing | `en__mda5dk` |
| 16 | phaseCue | shoulder_angle | backswing | down | arm back on the backswing | `en__1rvxll3` |
| 17 | phaseCue | shoulder_angle | followthrough | up | don't over-swing | `en__1qebifo` |
| 18 | phaseCue | shoulder_angle | followthrough | down | sweep up to finish | `en__1rpjuyk` |
| 19 | phaseCue | knee_bend | backswing | up | sit deeper to load | `en__1gee305` |
| 20 | phaseCue | knee_bend | backswing | down | don't over-sink | `en__nexz3j` |
| 21 | phaseCue | knee_bend | contact | up | stay down at contact | `en__r1p1ul` |
| 22 | phaseCue | knee_bend | contact | down | don't over-bend | `en__nfb2fx` |
| 23 | phaseCue | hip_flexion | backswing | up | hinge forward to load | `en__1w6cccu` |
| 24 | phaseCue | hip_flexion | backswing | down | hips up on the load | `en__59eh4s` |
| 25 | phaseCue | hip_flexion | contact | up | keep the hinge at contact | `en__7nhqfz` |
| 26 | phaseCue | hip_flexion | contact | down | hips up at contact | `en__qjncl3` |
| 27 | phaseCue | torso_lean | backswing | up | taller on the backswing | `en__19r6r5c` |
| 28 | phaseCue | torso_lean | backswing | down | lean in to load | `en__2e4fbt` |
| 29 | phaseCue | torso_lean | contact | up | taller at contact | `en__1x4iyaa` |
| 30 | phaseCue | torso_lean | contact | down | lean in at contact | `en__yr6ntd` |
| 31 | praise | - | - | #1 | that's the shape | `en__1kdrzf2` |
| 32 | praise | - | - | #2 | clean — repeat that | `en__1h8yn3e` |
| 33 | praise | - | - | #3 | correct | `en__1puczff` |

### UK — 33

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | зігни лікоть | `uk__sikz5h` |
| 2 | cue | elbow_angle | - | down | більше випрями руку | `uk__1lj8wq4` |
| 3 | cue | shoulder_angle | - | up | опусти плече | `uk__1w6czen` |
| 4 | cue | shoulder_angle | - | down | відкрий плече | `uk__vow359` |
| 5 | cue | knee_bend | - | up | зігни коліна | `uk__tj5386` |
| 6 | cue | knee_bend | - | down | стань вище | `uk__1d5f18x` |
| 7 | cue | torso_lean | - | up | тримайся рівніше | `uk__1285n6e` |
| 8 | cue | torso_lean | - | down | нахились уперед | `uk__18wiksz` |
| 9 | cue | shoulder_tilt | - | up | вирівняй плечі | `uk__1w98xz4` |
| 10 | cue | hip_flexion | - | up | вище | `uk__xvcsnn` |
| 11 | cue | hip_flexion | - | down | нахились у стегнах | `uk__mir2t` |
| 12 | phaseCue | elbow_angle | backswing | up | не блокуй лікоть на замаху | `uk__111yc4e` |
| 13 | phaseCue | elbow_angle | backswing | down | розправ лікоть на замаху | `uk__sxi94m` |
| 14 | phaseCue | elbow_angle | followthrough | up | доводь лікоть | `uk__1doqu7w` |
| 15 | phaseCue | elbow_angle | followthrough | down | не затискай на завершенні | `uk__1qllgx0` |
| 16 | phaseCue | shoulder_angle | backswing | up | рука нижче на замаху | `uk__1t78erx` |
| 17 | phaseCue | shoulder_angle | backswing | down | рука назад на замаху | `uk__cr9hxu` |
| 18 | phaseCue | shoulder_angle | followthrough | up | не перемахуй | `uk__ajdg9t` |
| 19 | phaseCue | shoulder_angle | followthrough | down | доводь руку вгору | `uk__1crmblp` |
| 20 | phaseCue | knee_bend | backswing | up | присядь глибше | `uk__prr3k8` |
| 21 | phaseCue | knee_bend | backswing | down | не присідай надто | `uk__ldzwat` |
| 22 | phaseCue | knee_bend | contact | up | тримай присід в ударі | `uk__2je8p9` |
| 23 | phaseCue | knee_bend | contact | down | не перегинай коліна | `uk__nscd61` |
| 24 | phaseCue | hip_flexion | backswing | down | стегна вище | `uk__11mteyj` |
| 25 | phaseCue | hip_flexion | contact | up | тримай нахил в ударі | `uk__mtjwg2` |
| 26 | phaseCue | hip_flexion | contact | down | стегна вище в ударі | `uk__1hiyn88` |
| 27 | phaseCue | torso_lean | backswing | up | рівніше на замаху | `uk__1kcaiuv` |
| 28 | phaseCue | torso_lean | backswing | down | нахились на замаху | `uk__mru8aj` |
| 29 | phaseCue | torso_lean | contact | up | рівніше в ударі | `uk__1m59u8k` |
| 30 | phaseCue | torso_lean | contact | down | нахились в ударі | `uk__a2hty0` |
| 31 | praise | - | - | #1 | оце форма | `uk__rkjcfy` |
| 32 | praise | - | - | #2 | чисто — повтори | `uk__restac` |
| 33 | praise | - | - | #3 | правильно | `uk__1nprot0` |

## Efficient (`preset-efficient`) — 54 phrases

### EN — 27

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | bend elbow | `en__6b808r` |
| 2 | cue | elbow_angle | - | down | extend arm | `en__19t3725` |
| 3 | cue | shoulder_angle | - | up | drop shoulder | `en__1huo6sy` |
| 4 | cue | shoulder_angle | - | down | open shoulder | `en__1h1ktnz` |
| 5 | cue | knee_bend | - | up | bend knees | `en__67a96m` |
| 6 | cue | knee_bend | - | down | stand taller | `en__xw0igb` |
| 7 | cue | torso_lean | - | up | taller | `en__stl3l3` |
| 8 | cue | torso_lean | - | down | lean in | `en__1edxc4` |
| 9 | cue | shoulder_tilt | - | up | level shoulders | `en__1n2poxq` |
| 10 | cue | hip_flexion | - | up | hips up | `en__87odpe` |
| 11 | cue | hip_flexion | - | down | hinge | `en__2xxlzc` |
| 12 | phaseCue | elbow_angle | backswing | up | don't lock elbow | `en__1gyp2bv` |
| 13 | phaseCue | elbow_angle | backswing | down | open elbow | `en__1yylleq` |
| 14 | phaseCue | elbow_angle | followthrough | up | finish elbow | `en__17ojuv9` |
| 15 | phaseCue | elbow_angle | followthrough | down | don't over-fold | `en__nfe0cx` |
| 16 | phaseCue | shoulder_angle | backswing | up | arm low | `en__d0eyb3` |
| 17 | phaseCue | shoulder_angle | backswing | down | arm back | `en__36zq28` |
| 18 | phaseCue | shoulder_angle | followthrough | up | don't over-swing | `en__1qebifo` |
| 19 | phaseCue | shoulder_angle | followthrough | down | sweep up | `en__9c3i44` |
| 20 | phaseCue | knee_bend | backswing | up | sit deeper | `en__ku586g` |
| 21 | phaseCue | knee_bend | backswing | down | don't over-sink | `en__nexz3j` |
| 22 | phaseCue | knee_bend | contact | up | stay down | `en__1lqjj7s` |
| 23 | phaseCue | knee_bend | contact | down | don't over-bend | `en__nfb2fx` |
| 24 | phaseCue | hip_flexion | contact | up | hold hinge | `en__1tr7wxz` |
| 25 | praise | - | - | #1 | clean | `en__2xc2g0` |
| 26 | praise | - | - | #2 | yes | `en__375zxm` |
| 27 | praise | - | - | #3 | good | `en__yix9l2` |

### UK — 27

| # | category | metric | phase | dir | phrase | clipKey |
|---|---|---|---|---|---|---|
| 1 | cue | elbow_angle | - | up | зігни лікоть | `uk__sikz5h` |
| 2 | cue | elbow_angle | - | down | випрями руку | `uk__pozuw1` |
| 3 | cue | shoulder_angle | - | up | опусти плече | `uk__1w6czen` |
| 4 | cue | shoulder_angle | - | down | відкрий плече | `uk__vow359` |
| 5 | cue | knee_bend | - | up | зігни коліна | `uk__tj5386` |
| 6 | cue | knee_bend | - | down | вище | `uk__xvcsnn` |
| 7 | cue | torso_lean | - | down | нахились | `uk__rxim2j` |
| 8 | cue | shoulder_tilt | - | up | рівніше плечі | `uk__1pi1ad3` |
| 9 | phaseCue | elbow_angle | backswing | up | не блокуй лікоть | `uk__1w26ixq` |
| 10 | phaseCue | elbow_angle | backswing | down | розправ лікоть | `uk__1gj7nzq` |
| 11 | phaseCue | elbow_angle | followthrough | up | доводь лікоть | `uk__1doqu7w` |
| 12 | phaseCue | elbow_angle | followthrough | down | не затискай | `uk__8hm8rm` |
| 13 | phaseCue | shoulder_angle | backswing | up | рука нижче | `uk__1jlle31` |
| 14 | phaseCue | shoulder_angle | backswing | down | рука назад | `uk__1jlgrde` |
| 15 | phaseCue | shoulder_angle | followthrough | up | не перемахуй | `uk__ajdg9t` |
| 16 | phaseCue | shoulder_angle | followthrough | down | руку вгору | `uk__pf90ar` |
| 17 | phaseCue | knee_bend | backswing | up | глибше присід | `uk__xmbtzx` |
| 18 | phaseCue | knee_bend | backswing | down | не пересідай | `uk__dmfezc` |
| 19 | phaseCue | knee_bend | contact | up | тримай присід | `uk__jmh1ke` |
| 20 | phaseCue | knee_bend | contact | down | не перегинай | `uk__bgpbwt` |
| 21 | phaseCue | hip_flexion | backswing | up | нахил | `uk__1gal4z2` |
| 22 | phaseCue | hip_flexion | backswing | down | стегна вище | `uk__11mteyj` |
| 23 | phaseCue | hip_flexion | contact | up | тримай нахил | `uk__15gon6p` |
| 24 | phaseCue | torso_lean | backswing | up | рівніше | `uk__vcrfef` |
| 25 | praise | - | - | #1 | чисто | `uk__1hq8ew7` |
| 26 | praise | - | - | #2 | так | `uk__36hpm5` |
| 27 | praise | - | - | #3 | добре | `uk__1hdpfsb` |

---
Total unique phrases across 3 styles: 192.
