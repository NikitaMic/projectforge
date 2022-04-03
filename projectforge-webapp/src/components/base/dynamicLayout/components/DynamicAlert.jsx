import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import PropTypes from 'prop-types';
import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { colorPropType } from '../../../../utilities/propTypes';
import { Alert } from '../../../design';
import { DynamicLayoutContext } from '../context';

function DynamicAlert(props) {
    const {
        message,
        markdown,
        title,
        id,
        color,
        icon,
    } = props;

    const { data, setData } = React.useContext(DynamicLayoutContext);

    const value = Object.getByString(data, id) || '';

    let box = value || message;

    if (markdown === true) {
        console.log(value, message);
        box = <ReactMarkdown remarkPlugins={[remarkGfm]}>{value || message}</ReactMarkdown>;
    }

    return React.useMemo(() => (
        <Alert color={color}>
            {title && (
                <h4 className="alert-heading">
                    {title}
                </h4>
            )}
            {icon && (
                <>
                    <FontAwesomeIcon icon={icon} />
                    &nbsp;&nbsp;
                </>
            )}
            {box}
        </Alert>
    ), [value, setData, id, props]);
}

DynamicAlert.propTypes = {
    message: PropTypes.string.isRequired,
    markdown: PropTypes.bool,
    title: PropTypes.string,
    color: colorPropType,
    icon: PropTypes.arrayOf(PropTypes.string),
};

DynamicAlert.defaultProps = {
    markdown: undefined,
    title: undefined,
    color: undefined,
    icon: undefined,
};

export default DynamicAlert;
