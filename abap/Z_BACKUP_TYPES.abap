*&---------------------------------------------------------------------*
*& DDIC types for the Backup & Restore Readiness RFC interface
*&---------------------------------------------------------------------*
*& Create these in SE11. Flat structures, so they can be used as RFC
*& table parameters without any conversion rules.
*&---------------------------------------------------------------------*

"! <p class="text">One backup job - the shape the readiness portal expects
"! from a live system.</p>
TYPES : BEGIN OF zrdy_bkp,
         backup_id   TYPE string,   " Backup identifier
         sys_name    TYPE string,   " System the backup belongs to
         backup_type TYPE string,   " FULL / INCREMENTAL / DIFFERENTIAL
         started_at  TYPE string,   " Start, YYYYMMDDHHMMSS
         finished_at TYPE string,   " End, YYYYMMDDHHMMSS
         duration_min TYPE int4,    " Duration in minutes
         size_gb     TYPE int4,     " Size of the backup file
         status      TYPE string,   " OK / FAILED / RUNNING
         media       TYPE string,   " Where the file was written
       END OF zrdy_bkp.

TYPES zrdy_bkp_tab TYPE STANDARD TABLE OF zrdy_bkp.
