# Tutored Trio — Design

## Goal

One tutor can supervise two beginners playing a practice game against each other.
When the weekly pairing would otherwise leave one player with a bye, form a
**tutored trio** (1 tutor + 2 learners) instead.

## Definitions

- **Players** — distinct Discord user IDs that voted Yes (index 1), Yes-Learning
  (index 3) or Yes-Teaching (index 4). "No" and "Already arranged" voters are excluded.
- **Learners** — Yes-Learning voters. **Tutors** — Yes-Teaching voters.

## Trigger

A trio is formed if and only if all hold:

1. the number of players is **odd**,
2. there are **at least 2 learners**,
3. there is **at least 1 tutor**.

Otherwise pairing behaves exactly as before (with an odd count, one player gets a bye).

## Trio selection

Random, no history involved:

- shuffle tutors (`Collections.shuffle`), take the first,
- shuffle learners, take the first two.

The three selected players are removed from their lists before the existing
pipeline runs.

## Remaining players

Unchanged pipeline in `PairingService.createPairings`:
bipartite learner↔tutor matching, then unmatched + Yes voters into the general
Blossom pool. The remaining count is even, so no bye is produced.

## Data model

- Migration `V3__add_tutor_to_pairings.sql`:
  `ALTER TABLE pairings ADD COLUMN tutor_discord_user_id VARCHAR(64);` (nullable, matches the player columns).
- `Pairing` entity: new nullable `tutorDiscordUserId`.
- A trio is persisted as **one row**: `player1 = learner1`, `player2 = learner2`,
  `tutor_discord_user_id = tutor`.
- `PairingResult` gains a third nullable component `tutorDiscordUserId`; a 2-arg
  constructor `(player1, player2)` is kept so existing call sites are unchanged.

## Pairing history

Unchanged. History (`BlossomPairingStrategy.buildPerPairStats`,
`LeastPlayedPairingStrategy`) reads only `player1`/`player2`, so a trio counts as a
single game between the two learners. The tutor's observing role does not affect
future pairing weights.

## Flow and message

- `PollClosedEventListener` maps `Pairing.tutorDiscordUserId` into `PairingResult`.
- `PairingsMessageService` renders a trio as a regular numbered match:
  `2. <@L1> vs <@L2> (tutored by <@T>)`.

## Examples

| Learners | Tutors | Yes | Before                    | After                     |
|----------|--------|-----|---------------------------|---------------------------|
| 2        | 1      | 0   | L–T, L bye                | T + L + L                 |
| 3        | 1      | 1   | L–T, L–Y, L bye           | T + L + L, L–Y            |
| 2        | 1      | 2   | L–T, L–Y, Y bye           | T + L + L, Y–Y            |
| 3        | 2      | 0   | L–T, L–T, L bye           | T + L + L, L–T            |
| 1        | 1      | 1   | L–T, Y bye                | unchanged (only 1 learner)|
| 2        | 0      | 1   | pool of 3: 1 pair + bye   | unchanged (no tutor)      |
| 2        | 1      | 1   | L–T, L–Y                  | unchanged (even)          |

## Testing

- `PairingServiceTest`: the trigger examples above — trio present with 1 tutor from
  tutors and 2 distinct learners from learners, no bye; even total → no trio;
  1 learner → bye; 0 tutors → bye. Members are random, so assert group membership,
  not exact IDs.
- `PairingsMessageServiceTest`: trio line format.
- `PollClosedEventListenerTest`: tutor mapped into the published `PairingResult`.
- Existing Testcontainers ITs validate the migration against the entity
  (`ddl-auto=validate`).

## Out of scope

- Counting tutoring duty / fairness between tutors.
- Trios when the player count is even.
- More than one trio per week.
