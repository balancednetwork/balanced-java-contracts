Issue:
SourceWeight and typeSum changes was not recorded at rounded times mean the have never taken affect.
This means SourceWeight and typeSum changes at exact times will most likely all be ZERO.
It was only recorded during vote changes if the vote had not already expired

Scenarios:
 * A User has ongoing votes which are not yet expired:
   * round vote end and add to change dbs
 * A User ongoing votes which are expired
   * round vote end and apply directly to sum and weight slopes like what should have happend the week they expired
 * A Users lock expired, they relocked and re voted