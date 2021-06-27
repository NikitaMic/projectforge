import classNames from 'classnames';
import PropTypes from 'prop-types';
import React from 'react';
import { tableColumnsPropType } from '../../../../../utilities/propTypes';
import Formatter from '../../../Formatter';
import DynamicCustomized from '../customized';
import style from './DynamicTable.module.scss';
import { DynamicLayoutContext } from '../../context';
import { getServiceURL, handleHTTPErrors } from '../../../../../utilities/rest';

function DynamicTableRow(
    {
        columns,
        row,
        highlightRow,
        rowClickPostUrl,
    },
) {
    const {
        callAction,
        data,
        setData,
    } = React.useContext(DynamicLayoutContext);

    const { template } = data;

    const handleRowClick = (rowId) => (event) => {
        event.stopPropagation();
        fetch(
            getServiceURL(`${rowClickPostUrl}/${rowId}`),
            {
                credentials: 'include',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                },
                body: JSON.stringify({
                    ...data,
                }),
            },
        )
            .then(handleHTTPErrors)
            .then((body) => body.json())
            .then((json) => {
                callAction({ responseAction: json });
            })
            // eslint-disable-next-line no-alert
            .catch((error) => alert(`Internal error: ${error}`));
        /*
        console.log(rowId);
        callAction({
            responseAction: {
                targetType: 'MODAL',
                url: evalServiceURL(`${rowClickPostUrl}/${rowId}`, {
                    category,
                    fileId: entry.fileId,
                    listId,
                }),
            },
        });
        */
    };

    return React.useMemo(() => (
        <tr
            className={classNames(
                style.clickable,
                { [style.highlighted]: highlightRow === true },
                { [style.deleted]: row.deleted === true },
            )}
            onClick={rowClickPostUrl ? handleRowClick(row.id) : undefined}
        >
            {columns.map((
                {
                    id,
                    dataType,
                    ...column
                },
            ) => (
                <td key={`table-body-row-${row.id}-column-${id}`}>
                    {dataType === 'CUSTOMIZED'
                        ? <DynamicCustomized id={id} data={row} />
                        : (
                            <Formatter
                                data={row}
                                id={id}
                                dataType={dataType}
                                {...column}
                            />
                        )}
                </td>
            ))}
        </tr>
    ), [setData, template]);
}

DynamicTableRow.propTypes = {
    columns: tableColumnsPropType.isRequired,
    row: PropTypes.shape({
        id: PropTypes.number.isRequired,
        deleted: PropTypes.bool,
    }).isRequired,
    highlightRow: PropTypes.bool,
    rowClickPostUrl: PropTypes.string,
};

DynamicTableRow.defaultProps = {
    highlightRow: false,
    rowClickPostUrl: undefined,
};

export default DynamicTableRow;
