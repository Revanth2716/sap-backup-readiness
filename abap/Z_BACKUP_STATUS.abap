*&---------------------------------------------------------------------*
*& Function Module Z_BACKUP_STATUS
*&---------------------------------------------------------------------*
*& RFC enabled function module that reads the backup catalog of the
*& landscape, so the Backup & Restore Readiness portal can run against a
*& live system instead of a spreadsheet somebody keeps by hand.
*&
*& CREATED WITH SE37 -> "Remote-Enabled" MUST BE TICKED.
*& Without that flag the function module is not registered for RFC and the
*& call fails with "not found / not remote enabled" - which appears in
*& SM58 on the calling system, not as a dump here.
*&
*& Source: M_BACKUP_CATALOG_FILES - the SAP HANA system view the database
*& itself maintains for every data and log backup it has taken. The view
*& holds the file name, the type, the start and end time, the size and the
*& state of each backup, which is exactly the evidence a recovery point
*& objective is measured against.
*&
*& On a landscape that is not HANA the equivalent evidence comes from the
*& DBA cockpit's backup log, or from the backup tool's own log files on
*& the application server; the structure of the interface does not change.
*&
*& What this function module deliberately does NOT do: it does not report
*& whether a restore has ever been tested. The database has no idea. That
*& evidence is a manual record, and it lives in the portal - which is
*& precisely the gap the readiness score is there to close.
*&---------------------------------------------------------------------*

FUNCTION z_backup_status.
*"*" Local Interface:
*"*" IMPORTING
*"*" VALUE(iv_system) TYPE string
*"*" VALUE(iv_limit) TYPE i DEFAULT 50
*"*" EXPORTING
*"*" VALUE(ev_count) TYPE i
*"*" VALUE(ev_message) TYPE string
*"*" TABLES
*"*" et_backups TYPE zrdy_bkp_tab

  DATA: lv_count TYPE i.

  CLEAR: ev_count, ev_message, et_backups.

  IF iv_system IS INITIAL.
    ev_message = 'No system was supplied.'.
    RETURN.
  ENDIF.

  " The catalog is read newest first, so the caller gets the most recent
  " recovery points at the top of the table.
  SELECT file_name file_id
         backup_type
         start_time
         end_time
         file_size
         state
         source_type
    FROM m_backup_catalog_files
    WHERE system_name = @iv_system
    ORDER BY start_time DESCENDING
    INTO TABLE @DATA(lt_catalog).

  LOOP AT lt_catalog INTO DATA(ls_catalog).
    IF lv_count >= iv_limit.
      EXIT.
    ENDIF.
    lv_count = lv_count + 1.

    " HANA writes the file size in bytes; the portal works in gigabytes
    " because that is how the data volume is discussed.
    DATA(lv_size_gb) = ls_catalog-file_size / 1024000000.

    " A backup that has not finished yet is not a recovery point.
    DATA(lv_status) = COND string(
      WHEN ls_catalog-state = 'OK'      THEN 'OK'
      WHEN ls_catalog-state = 'RUNNING' THEN 'RUNNING'
      ELSE 'FAILED' ).

    APPEND VALUE #(
      backup_id    = |{ ls_catalog-file_id }|
      sys_name     = iv_system
      backup_type  = COND string(
                        WHEN ls_catalog-backup_type = 'FULL'         THEN 'FULL'
                        WHEN ls_catalog-backup_type = 'INCREMENTAL'  THEN 'INCREMENTAL'
                        WHEN ls_catalog-backup_type = 'DIFFERENTIAL' THEN 'DIFFERENTIAL'
                        ELSE 'FULL' )
      started_at   = |{ ls_catalog-start_time DATE = TIME }|
      finished_at  = |{ ls_catalog-end_time DATE = TIME }|
      duration_min = COND #( WHEN ls_catalog-end_time IS INITIAL
                              THEN 0
                              ELSE
                                LET lv_diff = ls_catalog-end_time - ls_catalog-start_time
                                IN ( lv_diff / 60 ) )
      size_gb      = lv_size_gb
      status       = lv_status
      media        = |{ ls_catalog-source_type }|
    ) TO et_backups.
  ENDLOOP.

  ev_count = lv_count.

  IF ev_count = 0.
    ev_message = |No backup catalog entries found for { iv_system }.|.
  ELSE.
    ev_message = |{ ev_count } backup(s) returned for { iv_system }.|.
  ENDIF.

ENDFUNCTION.
